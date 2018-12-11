package com.bwz.study.syslog;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;

public class LogEventDecoder extends MessageToMessageDecoder<DatagramPacket> {

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        ByteBuf data = msg.content();
        int idx = data.indexOf(0, data.readableBytes(), LogEvent.SEPARATOR);
        String fliename = data.slice(0,idx).toString(CharsetUtil.UTF_8);
        String logMsg = data.slice(idx+1,data.readableBytes()).toString(CharsetUtil.UTF_8);

        LogEvent event = new LogEvent(msg.sender(), System.currentTimeMillis(), fliename,logMsg);
        out.add(event);
    }
}
