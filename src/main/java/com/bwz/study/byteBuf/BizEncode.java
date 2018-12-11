package com.bwz.study.byteBuf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 若我们的业务代码中需要访问ByteBuf中的数组时，那么我们应该使用堆缓冲区heapBuf
 */
public class BizEncode extends MessageToByteEncoder<Object> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        byte[] context = new byte[10];
        int length = 10;

        //在此使用堆缓冲区是为了更快的访问缓冲区内的数组，如果使用堆外缓冲区会多一次内核向堆内存的内存拷贝，这样会降低性能
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.heapBuffer(length);

        try {
            buf.writeBytes(context);
            byte[] dst = new byte[10];
            buf.readBytes(dst);
        } finally {
            // 使用完成后一定要记得释放到内存池中
            buf.release();
        }
    }
}
