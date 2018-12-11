import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

public class Test {

    public static void main(String[] args) throws Exception{
        //testA();
        testB();
    }

    public static void testC() {

    }

    public static void testB() throws Exception{
        ThreadLocal<Long> longThreadLocal = new ThreadLocal<Long>(){
            @Override
            protected Long initialValue() {
                return Thread.currentThread().getId();
            }
        };
        ThreadLocal<String> stringThreadLocal = new ThreadLocal<String>(){
            @Override
            protected String initialValue() {
                return Thread.currentThread().getName();
            }
        };

        //longThreadLocal.set(Thread.currentThread().getId());
        //stringThreadLocal.set(Thread.currentThread().getName());

        System.out.println(longThreadLocal.get());
        System.out.println(stringThreadLocal.get());

        Thread thread = new Thread(){
            public void run(){
                //longThreadLocal.set(Thread.currentThread().getId());
                //stringThreadLocal.set(Thread.currentThread().getName());

                System.out.println(longThreadLocal.get());
                System.out.println(stringThreadLocal.get());
            }
        };
        thread.start();
        thread.join();

        System.out.println(longThreadLocal.get());
        System.out.println(stringThreadLocal.get());
    }

    public static void testA() {
        int normalizedCapacity = 4096;
        normalizedCapacity --;
        normalizedCapacity |= normalizedCapacity >>>  1;
        //System.out.println(normalizedCapacity);
        normalizedCapacity |= normalizedCapacity >>>  2;
        normalizedCapacity |= normalizedCapacity >>>  4;
        normalizedCapacity |= normalizedCapacity >>>  8;
        normalizedCapacity |= normalizedCapacity >>> 16;
        normalizedCapacity ++;

        if (normalizedCapacity < 0) {
            normalizedCapacity >>>= 1;
        }

        int k = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(normalizedCapacity);

        System.out.println(normalizedCapacity);
        System.out.println(k);

        int d = 2;
        int n = 1 << d;
        System.out.println((byte) n);

        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false);
        ByteBuf buf = allocator.heapBuffer();
        ByteBuf buf1 = allocator.heapBuffer(2057);
        ByteBuf buf3 = allocator.heapBuffer(2077);
        ByteBuf buf2 = allocator.heapBuffer(16380);
        System.out.println(buf);
        System.out.println(buf1);
        System.out.println(buf3);
        System.out.println(buf2);
    }

    @org.junit.Test
    public void testD(){
        int length = 10;
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false);
        ByteBuf byteBuf = allocator.heapBuffer(length);
        byte[] context = new byte[length];

        try {
            testD0(byteBuf, context, 1000000);
            testD1(byteBuf, context, 1000000);
        } finally {
            byteBuf.release();
        }
    }

    private void testD0(ByteBuf buf, byte[] context, int r){
        long start = System.nanoTime();
        for(int i = 0; i < r; i++){
            buf.writeBytes(context);
            int length = buf.readableBytes();
            byte[] ret = buf.readBytes(length).array();
        }
        long end = System.nanoTime();
        System.out.println("长度读取耗时：" + (end - start) + "ns");
    }

    private void testD1(ByteBuf buf, byte[] context, int r){
        long start = System.nanoTime();
        for(int i = 0; i < r; i++){
            buf.writeBytes(context);
            int length = buf.readableBytes();
            byte[] ret = new byte[length];
            buf.readBytes(ret);
        }
        long end = System.nanoTime();
        System.out.println("数组读取耗时：" + (end - start) + "ns");
    }
}
