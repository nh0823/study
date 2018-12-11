import com.bwz.study.echo.RecyclerByCopyed;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RecycleTest {

    static class WrapRecycler {

        private List<String> list;

        private final static RecyclerByCopyed<WrapRecycler> RECYCLER = new RecyclerByCopyed<WrapRecycler>() {
            @Override
            protected WrapRecycler newObject(Handle<WrapRecycler> handle) {
                return new WrapRecycler(handle);
            }
        };

        RecyclerByCopyed.Handle<WrapRecycler> handle;

        WrapRecycler(RecyclerByCopyed.Handle<WrapRecycler> handle) {
            this.handle = handle;
            this.list = new ArrayList<>(1000);
        }

        List<String> getList() {
            return list;
        }

        static WrapRecycler getInstance() {
            return RECYCLER.get();
        }

        void recycle() {
            handle.recycle(this);
        }

    }

    @Test
    public void testDifferentThreadRecycle() throws Exception {
        System.out.println("Main thread started ...");
        final WrapRecycler instance00 = WrapRecycler.getInstance();
        instance00.getList().add("0000_0");
        final WrapRecycler instance01 = WrapRecycler.getInstance();
        instance01.getList().add("0000_1");
        final WrapRecycler instance1 = WrapRecycler.getInstance();
        instance1.getList().add("1111");
        final WrapRecycler instance2 = WrapRecycler.getInstance();
        instance2.getList().add("2222");
        final WrapRecycler instance3 = WrapRecycler.getInstance();
        instance3.getList().add("3333");
        final WrapRecycler instance4 = WrapRecycler.getInstance();
        instance4.getList().add("4444");
        final WrapRecycler instance5 = WrapRecycler.getInstance();
        instance5.getList().add("5555");
        final WrapRecycler instance6 = WrapRecycler.getInstance();
        instance6.getList().add("6666");
        final WrapRecycler instance7 = WrapRecycler.getInstance();
        instance7.getList().add("7777");
        final WrapRecycler instance8 = WrapRecycler.getInstance();
        instance8.getList().add("8888");
        final WrapRecycler instance9 = WrapRecycler.getInstance();
        instance9.getList().add("9999");
        final WrapRecycler instance10 = WrapRecycler.getInstance();
        instance10.getList().add("aaaa");

        instance00.recycle();
        instance01.recycle();

        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Sub thread-1 started ...");
                List<String> list1 = instance1.getList();
                list1.add("thread1 add");

                instance1.recycle();

                List<String> list3 = instance3.getList();
                list3.add("thread1 add");

                instance3.recycle();

                List<String> list5 = instance5.getList();
                list5.add("thread1 add");

                instance5.recycle();

                List<String> list7 = instance7.getList();
                list7.add("thread1 add");

                instance7.recycle();

                List<String> list9 = instance9.getList();
                list9.add("thread1 add");

                instance9.recycle();

                System.out.println("Sub Thread-1 get list : " + WrapRecycler.getInstance().getList());
            }
        });

        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Sub thread-2 started ...");
                List<String> list2 = instance2.getList();
                list2.add("thread2 add");

                instance2.recycle();

                List<String> list4 = instance4.getList();
                list4.add("thread2 add");

                instance4.recycle();

                List<String> list6 = instance6.getList();
                list6.add("thread2 add");

                instance6.recycle();

                List<String> list8 = instance8.getList();
                list8.add("thread2 add");

                instance8.recycle();

                List<String> list10 = instance10.getList();
                list10.add("thread2 add");

                instance10.recycle();

                System.out.println("Sub Thread-2 get list : " + WrapRecycler.getInstance().getList());
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        System.out.println("Main Thread get list : " + WrapRecycler.getInstance().getList());
        System.out.println("Main Thread get list : " + WrapRecycler.getInstance().getList());
        System.out.println("Main Thread get list : " + WrapRecycler.getInstance().getList());
        System.out.println("Main Thread get list : " + WrapRecycler.getInstance().getList());
        System.out.println("Main Thread get list : " + WrapRecycler.getInstance().getList());
        System.out.println("Main Thread get list : " + WrapRecycler.getInstance().getList());
        System.out.println("Main Thread get list : " + WrapRecycler.getInstance().getList());
        System.out.println("Main Thread get list : " + WrapRecycler.getInstance().getList());
        System.out.println("Main Thread get list : " + WrapRecycler.getInstance().getList());
        System.out.println("Main Thread get list : " + WrapRecycler.getInstance().getList());
    }
}
