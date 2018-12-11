package com.bwz.study.byteBuf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 若我们的业务代码只是为了将数据写入到ByteBuf中发送出去，那么我们需要使用堆外直接缓冲区directBuffer
 */
public class ZeroCopyEncode extends MessageToByteEncoder<Object> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        int length = 10;
        //在此使用堆外缓冲区是为了将数据更快速的写入内核中，如果使用堆缓冲区会多一次堆内存向堆外内存拷贝，这样会降低性能
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer(length);

        try {
            byte[] context = new byte[length];
            buf.writeBytes(context);

            out.writeBytes(buf);
        } finally {
            //必须释放自己申请的内存池缓冲区，否则会内存泄露
            //out是Netty自身Socket发送的ByteBuf系统会自动释放，用户不需要做二次释放
            buf.release();
        }
    }
}
