package com.bwz.study.echo;

import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.util.internal.MathUtil.safeFindNextPositivePowerOfTwo;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Light-weight object pool based on a thread-local stack.   一个轻量的对象池
 *
 * @param <T> the type of the pooled object
 */
public abstract class RecyclerByCopyed<T> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(RecyclerByCopyed.class);

    @SuppressWarnings("rawtypes")
    private static final RecyclerByCopyed.Handle NOOP_HANDLE = new RecyclerByCopyed.Handle() {   //表示一个不需要回收的包装对象
        @Override
        public void recycle(Object object) {     //
            // NOOP
        }
    };
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(Integer.MIN_VALUE);
    private static final int OWN_THREAD_ID = ID_GENERATOR.getAndIncrement();
    private static final int DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD = 4 * 1024; // Use 4k instances as default.默认最多缓存4K个对象
    private static final int DEFAULT_MAX_CAPACITY_PER_THREAD;      //每个线程的Stack最多缓存多少个对象
    private static final int INITIAL_CAPACITY;                     //初始化容量
    private static final int MAX_SHARED_CAPACITY_FACTOR;           //最大可共享容量
    private static final int MAX_DELAYED_QUEUES_PER_THREAD;        //WeakOrderqueue最大数量
    private static final int LINK_CAPACITY;                        //WeakOrderqueue中的数组DefaultHandle<?>[] elements的容量
    private static final int RATIO;                                //掩码

    static {
        // In the future, we might have different maxCapacity for different object types.
        // e.g. io.netty.recycler.maxCapacity.writeTask
        //      io.netty.recycler.maxCapacity.outboundBuffer
        //这里做到了各种参数都是可配置的，可以根据实际压测情况，调节对象池的参数
        int maxCapacityPerThread = SystemPropertyUtil.getInt("io.netty.recycler.maxCapacityPerThread",
                SystemPropertyUtil.getInt("io.netty.recycler.maxCapacity", DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD));
        if (maxCapacityPerThread < 0) {
            maxCapacityPerThread = DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD;
        }

        //设置最大缓存容量
        DEFAULT_MAX_CAPACITY_PER_THREAD = maxCapacityPerThread;

        MAX_SHARED_CAPACITY_FACTOR = max(2,   //最大可共享容量因子为2
                SystemPropertyUtil.getInt("io.netty.recycler.maxSharedCapacityFactor",
                        2));

        MAX_DELAYED_QUEUES_PER_THREAD = max(0,    //WeakOrderQueue中的最大容量设置
                SystemPropertyUtil.getInt("io.netty.recycler.maxDelayedQueuesPerThread",
                        // We use the same value as default EventLoop number
                        NettyRuntime.availableProcessors() * 2));    //设置成和默认EventLoop相同的数量

        LINK_CAPACITY = safeFindNextPositivePowerOfTwo(
                max(SystemPropertyUtil.getInt("io.netty.recycler.linkCapacity", 16), 16));
        //DefaultHandler[] 数组的数量默认设置成16

        // By default we allow one push to a Recycler for each 8th try on handles that were never recycled before.
        // This should help to slowly increase the capacity of the recycler while not be too sensitive to allocation
        // bursts.
        RATIO = safeFindNextPositivePowerOfTwo(SystemPropertyUtil.getInt("io.netty.recycler.ratio", 8));
        //默认值设置为8，也就是二进制为1000，这样设置可以让容量缓慢增大，避免爆发式的请求，默认每过8次就允许进行回收一次

        if (logger.isDebugEnabled()) {
            if (DEFAULT_MAX_CAPACITY_PER_THREAD == 0) {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: disabled");
                logger.debug("-Dio.netty.recycler.maxSharedCapacityFactor: disabled");
                logger.debug("-Dio.netty.recycler.linkCapacity: disabled");
                logger.debug("-Dio.netty.recycler.ratio: disabled");
            } else {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: {}", DEFAULT_MAX_CAPACITY_PER_THREAD);
                logger.debug("-Dio.netty.recycler.maxSharedCapacityFactor: {}", MAX_SHARED_CAPACITY_FACTOR);
                logger.debug("-Dio.netty.recycler.linkCapacity: {}", LINK_CAPACITY);
                logger.debug("-Dio.netty.recycler.ratio: {}", RATIO);
            }
        }

        INITIAL_CAPACITY = min(DEFAULT_MAX_CAPACITY_PER_THREAD, 256);
        //初始容量取默认值4K和256的最小值
    }

    private final int maxCapacityPerThread;          //最大容量
    private final int maxSharedCapacityFactor;       //最大可共享容量因子
    private final int ratioMask;                     //掩码
    private final int maxDelayedQueuesPerThread;     //WeakOrderQueue的最大容量

    //FastThreadLocal是线程本地变量，所以每个线程都对应一个自己的Stack
    //通过threadLocal.get()可以获得一个RecyclerByCopyed.Stack对象
    private final FastThreadLocal<RecyclerByCopyed.Stack<T>> threadLocal = new FastThreadLocal<RecyclerByCopyed.Stack<T>>() {
        //当线程中的InternalThreadLocalMap中没有找到RecyclerByCopyed.Stack对象实例时，在这里初始化一个，并返回
        @Override
        protected RecyclerByCopyed.Stack<T> initialValue() {
            return new RecyclerByCopyed.Stack<T>(RecyclerByCopyed.this, Thread.currentThread(), maxCapacityPerThread, maxSharedCapacityFactor,
                    ratioMask, maxDelayedQueuesPerThread);
        }

        @Override
        protected void onRemoval(RecyclerByCopyed.Stack<T> value) {
            // Let us remove the WeakOrderQueue from the WeakHashMap directly if its safe to remove some overhead
            if (value.threadRef.get() == Thread.currentThread()) {
                if (DELAYED_RECYCLED.isSet()) {
                    DELAYED_RECYCLED.get().remove(value);
                }
            }
        }
    };

    protected RecyclerByCopyed() {
        this(DEFAULT_MAX_CAPACITY_PER_THREAD);
    }

    protected RecyclerByCopyed(int maxCapacityPerThread) {
        this(maxCapacityPerThread, MAX_SHARED_CAPACITY_FACTOR);
    }

    protected RecyclerByCopyed(int maxCapacityPerThread, int maxSharedCapacityFactor) {
        this(maxCapacityPerThread, maxSharedCapacityFactor, RATIO, MAX_DELAYED_QUEUES_PER_THREAD);
    }

    protected RecyclerByCopyed(int maxCapacityPerThread, int maxSharedCapacityFactor,
                       int ratio, int maxDelayedQueuesPerThread) {
        ratioMask = safeFindNextPositivePowerOfTwo(ratio) - 1;   //为2的幂次-1，  默认为8， 则该值为0000 0111
        if (maxCapacityPerThread <= 0) {
            this.maxCapacityPerThread = 0;
            this.maxSharedCapacityFactor = 1;
            this.maxDelayedQueuesPerThread = 0;
        } else {
            this.maxCapacityPerThread = maxCapacityPerThread;
            this.maxSharedCapacityFactor = max(1, maxSharedCapacityFactor);
            this.maxDelayedQueuesPerThread = max(0, maxDelayedQueuesPerThread);
        }
    }

    //从RecyclerByCoped中获取一个可以复用的对象，如果没有，则新建一个对象返回
    @SuppressWarnings("unchecked")
    public final T get() {
        if (maxCapacityPerThread == 0) {      // 通过修改maxCapacityPerThread=0可以关闭回收功能，默认值是4k
            return newObject((RecyclerByCopyed.Handle<T>) NOOP_HANDLE);
        }
        RecyclerByCopyed.Stack<T> stack = threadLocal.get();      //通过FastThreadLocal.get()得到Stack对象
        RecyclerByCopyed.DefaultHandle<T> handle = stack.pop();   //从Stack中弹出一个DefaultHandle
        if (handle == null) {
            handle = stack.newHandle();                           //Stack中没有Handle对象，则新建一个对象
            handle.value = newObject(handle);                     //调用子类的newObject()方法创建一个新的对象，并将该值赋予Handle管理
        }
        return (T) handle.value;                                  //返回从Stack中弹出的Handle中管理的对象
    }

    /**
     * @deprecated use {@link RecyclerByCopyed.Handle#recycle(Object)}.  //旧的方法，已经废除
     */
    @Deprecated
    public final boolean recycle(T o, RecyclerByCopyed.Handle<T> handle) {
        if (handle == NOOP_HANDLE) {
            return false;
        }

        RecyclerByCopyed.DefaultHandle<T> h = (RecyclerByCopyed.DefaultHandle<T>) handle;
        if (h.stack.parent != this) {    //旧方法，如果不是当前线程的，直接不回收了
            return false;
        }

        h.recycle(o);
        return true;
    }

    final int threadLocalCapacity() {
        return threadLocal.get().elements.length;
    }

    final int threadLocalSize() {
        return threadLocal.get().size;
    }

    //抽象方法，实现RecyclerByCopyed类需要给出具体实例的new 方法
    protected abstract T newObject(RecyclerByCopyed.Handle<T> handle);

    //回收器接口
    public interface Handle<T> {
        void recycle(T object);
    }

    //DefaultHandle就是以Stack的包装对象，持有Stack的引用，可以回收自己到Stack中
    static final class DefaultHandle<T> implements RecyclerByCopyed.Handle<T> {
        private int lastRecycledId;   //标记最新一次回收的线程ID
        private int recycleId;        //也是一个标记，是用来回收前的校验的

        boolean hasBeenRecycled;      //标记是否已经被回收

        private RecyclerByCopyed.Stack<?> stack;     //持有stack的引用
        private Object value;                        //持有需要回收器管理的对象，这里可以发现一个回收器管理一个回收对象

        DefaultHandle(RecyclerByCopyed.Stack<?> stack) {
            this.stack = stack;
        }

        @Override
        public void recycle(Object object) {
            if (object != value) {
                throw new IllegalArgumentException("object does not belong to handle");
            }
            stack.push(this);                //如果需要回收的对象符合该回收器，则将该回收器押入stack中
        }
    }

    //FastThreadLocal是线程本地变量，所以每个线程都对应一个自己的Stack
    //这个也是一个线程本地变量，所以每个线程都对应一个自己的Map<RecyclerByCopyed.Stack<?>, RecyclerByCopyed.WeakOrderQueue>
    //根据Stack可以获取对应的WeakOrderQueue,注意这里的两个对象都有弱引用
    private static final FastThreadLocal<Map<RecyclerByCopyed.Stack<?>, RecyclerByCopyed.WeakOrderQueue>> DELAYED_RECYCLED =
            new FastThreadLocal<Map<RecyclerByCopyed.Stack<?>, RecyclerByCopyed.WeakOrderQueue>>() {
                //初始化一个Map<RecyclerByCopyed.Stack<?>, RecyclerByCopyed.WeakOrderQueue>返回
                @Override
                protected Map<RecyclerByCopyed.Stack<?>, RecyclerByCopyed.WeakOrderQueue> initialValue() {
                    //使用WeakHashMap，保证对Key也就是Stack是弱引用，一旦Stack没有强引用了，会被回收，WeakHashMap不会无限占用内存
                    return new WeakHashMap<RecyclerByCopyed.Stack<?>, RecyclerByCopyed.WeakOrderQueue>();
                }
            };

    // a queue that makes only moderate guarantees about visibility: items are seen in the correct order,
    // but we aren't absolutely guaranteed to ever see anything at all, thereby keeping the queue cheap to maintain
    private static final class WeakOrderQueue {

        //用于标记空的WeakOrderQueue，在达到WeakOrderQueue数量上限时放入一个这个，表示结束了
        static final RecyclerByCopyed.WeakOrderQueue DUMMY = new RecyclerByCopyed.WeakOrderQueue();

        //Link对象本身会作为读索引
        // Let Link extend AtomicInteger for intrinsics. The Link itself will be used as writerIndex.
        @SuppressWarnings("serial")
        private static final class Link extends AtomicInteger {  //这里为什么要继承一个AtomicInteger，因为这样Link就是一个线程安全的容器，保证多线程安全和可见性
            //维护一个数组，容量默认为16
            private final RecyclerByCopyed.DefaultHandle<?>[] elements = new RecyclerByCopyed.DefaultHandle[LINK_CAPACITY];

            private int readIndex;  //读索引
            private RecyclerByCopyed.WeakOrderQueue.Link next;  //下一个索引，WeakOrderQueue有多个时，之间遍历靠next指向下一个WeakOrderQueue
        }

        // chain of data items
        private RecyclerByCopyed.WeakOrderQueue.Link head, tail;   //头指针，尾指针
        // pointer to another queue of delayed items for the same stack
        private RecyclerByCopyed.WeakOrderQueue next;              //指向下一个WeakOrderQueue
        private final WeakReference<Thread> owner;    //拥有者，这是一个弱引用
        private final int id = ID_GENERATOR.getAndIncrement();   //WeakOrderQueue的唯一标记
        private final AtomicInteger availableSharedCapacity;     //允许的最大共享容量

        private WeakOrderQueue() {           //用于初始化DUMMY，遇到DUMMY就知道要抛弃了
            owner = null;
            availableSharedCapacity = null;
        }

        private WeakOrderQueue(RecyclerByCopyed.Stack<?> stack, Thread thread) {     //在Stack的pushLater()中如果没有WeakOrderQueue，会调用这里new一个
            head = tail = new RecyclerByCopyed.WeakOrderQueue.Link();                //初始化头和尾指针，指向这个新创建的Link
            owner = new WeakReference<Thread>(thread);                               //表示当前的WeakOrderQueue是被哪个线程拥有的，因为只有不同线程去回收对象才会进到这个方法，所以thread不是这个是stack对应的线程
                                                            //这里使用的是弱引用，在线程没有强引用的时候，线程也是可以被回收的对象

            //这里很重要，我们没有把Stack保存到WeakOrderQueue中
            //因为Stack是WeakHashMap的key
            //我们只是持有head和tail的引用，就可以遍历WeakOrderQueue
            // Its important that we not store the Stack itself in the WeakOrderQueue as the Stack also is used in
            // the WeakHashMap as key. So just store the enclosed AtomicInteger which should allow to have the
            // Stack itself GCed.
            availableSharedCapacity = stack.availableSharedCapacity;
        }

        //stack.setHead(queue)必须在构造器外进行，防止对象溢出
        static RecyclerByCopyed.WeakOrderQueue newQueue(RecyclerByCopyed.Stack<?> stack, Thread thread) {
            RecyclerByCopyed.WeakOrderQueue queue = new RecyclerByCopyed.WeakOrderQueue(stack, thread);
            // Done outside of the constructor to ensure WeakOrderQueue.this does not escape the constructor and so
            // may be accessed while its still constructed.
            stack.setHead(queue);   //这个stack，头指针指向这个新创建的WeakOrderQueue
            return queue;
        }

        private void setNext(RecyclerByCopyed.WeakOrderQueue next) {
            assert next != this;
            this.next = next;
        }

        /**
         * Allocate a new {@link RecyclerByCopyed.WeakOrderQueue} or return {@code null} if not possible.
         */
        static RecyclerByCopyed.WeakOrderQueue allocate(RecyclerByCopyed.Stack<?> stack, Thread thread) {
            // We allocated a Link so reserve the space
            return reserveSpace(stack.availableSharedCapacity, LINK_CAPACITY)   //先预约space容量
                    ? newQueue(stack, thread) : null;                           //预约成功，对当前stack创建一个新的WeakOrderQueue
        }

        //容量不够就返回false，够的化就减去space大小
        private static boolean reserveSpace(AtomicInteger availableSharedCapacity, int space) {
            assert space >= 0;
            for (;;) {
                int available = availableSharedCapacity.get();
                if (available < space) {    //如果剩余可用容量小于 LINK_CAPACITY，则返回false
                    return false;
                }
                if (availableSharedCapacity.compareAndSet(available, available - space)) {  //调用availableSharedCapacity线程安全的CAS方法
                    return true;
                }
            }
        }

        private void reclaimSpace(int space) {   //availableSharedCapacity加上space，就是恢复前面减去的space大小
            assert space >= 0;
            availableSharedCapacity.addAndGet(space);
        }

        void add(RecyclerByCopyed.DefaultHandle<?> handle) {
            handle.lastRecycledId = id;          //更新最近一次回收的ID，注意这里只更新了lastRecycledId， recycleId没有更新，等到真正回收的时候，会改成一致的

            RecyclerByCopyed.WeakOrderQueue.Link tail = this.tail;
            int writeIndex;
            if ((writeIndex = tail.get()) == LINK_CAPACITY) {
                if (!reserveSpace(availableSharedCapacity, LINK_CAPACITY)) {    //判断剩余空间是否足够
                    // Drop it.
                    return;
                }
                // We allocate a Link so reserve the space
                this.tail = tail = tail.next = new RecyclerByCopyed.WeakOrderQueue.Link();

                writeIndex = tail.get();      //
            }
            tail.elements[writeIndex] = handle;     //把对应的handle引用放到末尾的数组里
            handle.stack = null;
            // we lazy set to ensure that setting stack to null appears before we unnull it in the owning thread;
            // this also means we guarantee visibility of an element in the queue if we see the index updated
            tail.lazySet(writeIndex + 1);
        }

        boolean hasFinalData() {
            return tail.readIndex != tail.get();          //readIndex指向当前读取的，get表示最大的值，不相等代表还有待读取的数据
        }

        // transfer as many items as we can from this queue to the stack, returning true if any were transferred
        @SuppressWarnings("rawtypes")
        boolean transfer(RecyclerByCopyed.Stack<?> dst) {       //把WeakOrderQueue里面暂存的对象，传输到对应的stack，主动去回收对象
            RecyclerByCopyed.WeakOrderQueue.Link head = this.head;
            if (head == null) {
                return false;
            }

            if (head.readIndex == LINK_CAPACITY) {
                if (head.next == null) {
                    return false;
                }
                this.head = head = head.next;
            }

            final int srcStart = head.readIndex;
            int srcEnd = head.get();
            final int srcSize = srcEnd - srcStart;
            if (srcSize == 0) {
                return false;
            }

            final int dstSize = dst.size;
            final int expectedCapacity = dstSize + srcSize;

            if (expectedCapacity > dst.elements.length) {
                final int actualCapacity = dst.increaseCapacity(expectedCapacity);   //扩容
                srcEnd = min(srcStart + actualCapacity - dstSize, srcEnd);           //获取目标stack最多可以保存的容量
            }

            if (srcStart != srcEnd) {
                final RecyclerByCopyed.DefaultHandle[] srcElems = head.elements;
                final RecyclerByCopyed.DefaultHandle[] dstElems = dst.elements;
                int newDstSize = dstSize;
                for (int i = srcStart; i < srcEnd; i++) {
                    RecyclerByCopyed.DefaultHandle element = srcElems[i];     //拿到源回收器
                    if (element.recycleId == 0) {                             //只有recycleId=0才表示可以回收
                        element.recycleId = element.lastRecycledId;             //前面的add方法只更新了lastRecycledId， transfer执行好了，需要更新recycleId一致，表示回收成功
                    } else if (element.recycleId != element.lastRecycledId) {
                        throw new IllegalStateException("recycled already");
                    }
                    srcElems[i] = null;                                       //成功了，就把WeakOrderQueue数组里置为空，释放对对象的引用

                    if (dst.dropHandle(element)) {                            //判断是否回收
                        // Drop the object.
                        continue;
                    }
                    element.stack = dst;                                      //element是Link数组里的对象，stack指向目标stack
                    dstElems[newDstSize ++] = element;                        //在目标的stack数组的尾部放入element
                }

                if (srcEnd == LINK_CAPACITY && head.next != null) {           //如果head.next还有，就需要继续扩容
                    // Add capacity back as the Link is GCed.
                    reclaimSpace(LINK_CAPACITY);                              //扩容

                    this.head = head.next;                                    //指向下一个，等待下一次循环继续上面的操作，transfer方法外层是被循环调用的
                }

                head.readIndex = srcEnd;                                      //下次从这里开始读
                if (dst.size == newDstSize) {                                 //如果相等则表示没有剩余空间了，返回false
                    return false;
                }
                dst.size = newDstSize;                                        //目标数组size修改
                return true;
            } else {
                // The destination stack is full already.
                return false;                                                 //目标数组仍然是满的，直接返回false，就不做回收动作
            }
        }

        @Override
        protected void finalize() throws Throwable {                          //WeakOrderQueue对象GC前调用这个方法
            try {
                super.finalize();                                             //回收对象
            } finally {
                // We need to reclaim all space that was reserved by this WeakOrderQueue so we not run out of space in
                // the stack. This is needed as we not have a good life-time control over the queue as it is used in a
                // WeakHashMap which will drop it at any time.
                // 需要这个方法是因为这个被用在WeakHashMap中，会随时GC它
                RecyclerByCopyed.WeakOrderQueue.Link link = head;
                while (link != null) {    //遍历WeakHashMap中所有的Link，知道link=null，这样里面的Link对象没有引用了，都会被回收
                    reclaimSpace(LINK_CAPACITY);
                    link = link.next;
                }
            }
        }
    }

    static final class Stack<T> {

        // we keep a queue of per-thread queues, which is appended to once only, each time a new thread other
        // than the stack owner recycles: when we run out of items in our stack we iterate this collection
        // to scavenge those that can be reused. this permits us to incur minimal thread synchronisation whilst
        // still recycling all items.
        //最小化同步操作，并且可以全部回收
        final RecyclerByCopyed<T> parent;

        // We store the Thread in a WeakReference as otherwise we may be the only ones that still hold a strong
        // Reference to the Thread itself after it died because DefaultHandle will hold a reference to the Stack.
        //
        // The biggest issue is if we do not use a WeakReference the Thread may not be able to be collected at all if
        // the user will store a reference to the DefaultHandle somewhere and never clear this reference (or not clear
        // it in a timely manner).
        //如果持有线程的强引用，如果stack没有被回收，那么Thread也不能被回收了，但是Stack没有强引用，在map中是弱引用
        final WeakReference<Thread> threadRef;            //持有到线程的弱引用
        final AtomicInteger availableSharedCapacity;      //最大可共享的容量，可进行CAS修改
        final int maxDelayedQueues;                       //WeakOrderQueue的最大容量

        private final int maxCapacity;                    //最大容量
        private final int ratioMask;                      //掩码
        private RecyclerByCopyed.DefaultHandle<?>[] elements;        //DefaultHandle数组
        private int size;
        private int handleRecycleCount = -1; // Start with -1 so the first one will be recycled.
        private RecyclerByCopyed.WeakOrderQueue cursor, prev;       //指向当前的WeakOrderQueue 和 前一个
        private volatile RecyclerByCopyed.WeakOrderQueue head;

        Stack(RecyclerByCopyed<T> parent, Thread thread, int maxCapacity, int maxSharedCapacityFactor,
              int ratioMask, int maxDelayedQueues) {
            this.parent = parent;
            threadRef = new WeakReference<Thread>(thread);
            this.maxCapacity = maxCapacity;
            availableSharedCapacity = new AtomicInteger(max(maxCapacity / maxSharedCapacityFactor, LINK_CAPACITY));   //maxSharedCapacityFactor默认为2
            elements = new RecyclerByCopyed.DefaultHandle[min(INITIAL_CAPACITY, maxCapacity)]; //取初始化和maxCapacity的最小值，一般保留maxCapacity，方便之后的动态扩容
            this.ratioMask = ratioMask;
            this.maxDelayedQueues = maxDelayedQueues;
        }

        // Marked as synchronized to ensure this is serialized.
        //标记为同步，保证两个操作顺序执行
        synchronized void setHead(RecyclerByCopyed.WeakOrderQueue queue) {
            queue.setNext(head);   //synchronized避免并修改queue.setNext的情况
            head = queue;
        }

        int increaseCapacity(int expectedCapacity) {
            int newCapacity = elements.length;
            int maxCapacity = this.maxCapacity;
            do {
                newCapacity <<= 1;    //每次扩容两倍，知道newCapacity>expectedCapacity
            } while (newCapacity < expectedCapacity && newCapacity < maxCapacity);

            newCapacity = min(newCapacity, maxCapacity);
            if (newCapacity != elements.length) {
                elements = Arrays.copyOf(elements, newCapacity);
            }

            return newCapacity;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        RecyclerByCopyed.DefaultHandle<T> pop() {
            int size = this.size;
            if (size == 0) {
                if (!scavenge()) {
                    return null;
                }
                size = this.size;
            }
            size --;
            RecyclerByCopyed.DefaultHandle ret = elements[size];
            elements[size] = null;
            if (ret.lastRecycledId != ret.recycleId) {           //这两个应该相等
                throw new IllegalStateException("recycled multiple times");
            }
            ret.recycleId = 0;                                   //获取出的对象，置为0表示没有被回收
            ret.lastRecycledId = 0;                              //获取出的对象，置为0表示没有被回收
            this.size = size;
            return ret;
        }

        boolean scavenge() {
            // continue an existing scavenge, if any
            if (scavengeSome()) {
                return true;
            }

            // reset our scavenge cursor
            prev = null;
            cursor = head;
            return false;
        }

        boolean scavengeSome() {   //尝试回收
            RecyclerByCopyed.WeakOrderQueue prev;
            RecyclerByCopyed.WeakOrderQueue cursor = this.cursor;     //指向当前的指针
            if (cursor == null) {                                     //当前指针为null,就指向head，head为null就跳出返回false
                prev = null;
                cursor = head;
                if (cursor == null) {
                    return false;
                }
            } else {
                prev = this.prev;
            }

            boolean success = false;
            do {
                if (cursor.transfer(this)) {
                    success = true;
                    break;
                }
                RecyclerByCopyed.WeakOrderQueue next = cursor.next;
                if (cursor.owner.get() == null) {                    // 线程被回收了
                    // If the thread associated with the queue is gone, unlink it, after
                    // performing a volatile read to confirm there is no data left to collect.
                    // We never unlink the first queue, as we don't want to synchronize on updating the head.
                    //这里读取的是线程安全的变量，确认没有数据可回收了
                    //第一个queue用于不回收，因为更新head指针会存在并发
                    if (cursor.hasFinalData()) {
                        for (;;) {
                            if (cursor.transfer(this)) {
                                success = true;
                            } else {
                                break;     //cursor.transfer(this)返回false，代表没有读取的数据了
                            }
                        }
                    }

                    if (prev != null) {
                        prev.setNext(next);    //这是一个单向链表，只要改变prev的引用，老的节点会被回收
                    }
                } else {
                    prev = cursor;
                }

                cursor = next;

            } while (cursor != null && !success);

            this.prev = prev;
            this.cursor = cursor;
            return success;
        }

        void push(RecyclerByCopyed.DefaultHandle<?> item) {      //会综合判断，如果是当前线程，之间放进数组中，如果不是，就先放到WeakOrderQueue中
            Thread currentThread = Thread.currentThread();
            if (threadRef.get() == currentThread) {
                // The current Thread is the thread that belongs to the Stack, we can try to push the object now.
                pushNow(item);
            } else {
                // The current Thread is not the one that belongs to the Stack
                // (or the Thread that belonged to the Stack was collected already), we need to signal that the push
                // happens later.
                pushLater(item, currentThread);         //保存到WeakOrderQueue，等待回收
            }
        }

        private void pushNow(RecyclerByCopyed.DefaultHandle<?> item) {    //立即push，把对象回收到elements数组中
            if ((item.recycleId | item.lastRecycledId) != 0) {            //如果每回收，recycleId和lastRecycledId都是0
                throw new IllegalStateException("recycled already");
            }
            item.recycleId = item.lastRecycledId = OWN_THREAD_ID;         //都更新为OWN_THREAD_ID，表示被回收过了

            int size = this.size;
            if (size >= maxCapacity || dropHandle(item)) {                //如果size >= maxCapacity就直接返回，不进行回收
                // Hit the maximum capacity or should drop - drop the possibly youngest object.
                return;                                                   //dropHandle(item)返回true，本次不做回收
            }
            if (size == elements.length) {
                elements = Arrays.copyOf(elements, min(size << 1, maxCapacity));    //扩容
            }

            elements[size] = item;     //这里需要注意：elements.length是数组的长度，包括空位；size是数组中有内容的长度，这里是在最末尾放item
            this.size = size + 1;
        }

        private void pushLater(RecyclerByCopyed.DefaultHandle<?> item, Thread thread) {
            // we don't want to have a ref to the queue as the value in our weak map
            // so we null it out; to ensure there are no races with restoring it later
            // we impose a memory ordering here (no-op on x86)
            //为了在回收的过程中没有并发，如果回收的不是当前线程的Stack的对象
            //就放入到它的WeakOrderQueue，等它自己拿的时候回收，这样recycle方法就没有并发了；这种思想在Doug lea的AQS里也有
            //获取当前线程对应的Map<RecyclerByCopyed.Stack<?>, RecyclerByCopyed.WeakOrderQueue>
            Map<RecyclerByCopyed.Stack<?>, RecyclerByCopyed.WeakOrderQueue> delayedRecycled = DELAYED_RECYCLED.get();
            //根据this Stack获取对应的WeakOrderQueue
            RecyclerByCopyed.WeakOrderQueue queue = delayedRecycled.get(this);
            //如果queue为null
            if (queue == null) {
                //如果容量大于上限，就放入一个DUMMY，表示满了
                if (delayedRecycled.size() >= maxDelayedQueues) {
                    // Add a dummy queue so we know we should drop the object
                    delayedRecycled.put(this, RecyclerByCopyed.WeakOrderQueue.DUMMY);
                    return;
                }
                // Check if we already reached the maximum number of delayed queues and if we can allocate at all.
                //WeakOrderQueue.allocate方法，针对需要回收的这个stack，创建一个新的WeakOrderQueue
                if ((queue = RecyclerByCopyed.WeakOrderQueue.allocate(this, thread)) == null) {
                    // drop object
                    return;
                }
                delayedRecycled.put(this, queue);
            } else if (queue == RecyclerByCopyed.WeakOrderQueue.DUMMY) {
                // drop object
                return;
            }

            queue.add(item);
        }

        boolean dropHandle(RecyclerByCopyed.DefaultHandle<?> handle) {
            if (!handle.hasBeenRecycled) {           //判断是否已经回收
                if ((++handleRecycleCount & ratioMask) != 0) {     //handleRecycleCount初始为-1，所以第一次肯定会进去
                    // Drop the object.
                    return true;
                }
                handle.hasBeenRecycled = true;
            }
            return false;
        }

        RecyclerByCopyed.DefaultHandle<T> newHandle() {
            return new RecyclerByCopyed.DefaultHandle<T>(this);      //返回一个指向该Stack的DefaultHandle
        }
    }
}
