package com.bwz.study.syslog;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;

public class LogEventHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        String result = msg.content().toString(CharsetUtil.UTF_8);
        System.out.println(result);
        ctx.close();
    }
}