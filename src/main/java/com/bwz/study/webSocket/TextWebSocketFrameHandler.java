package com.bwz.study.webSocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class TextWebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final ChannelGroup group;

    public TextWebSocketFrameHandler(ChannelGroup group){
        this.group = group;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        Channel incoming = ctx.channel();
        for(Channel channel:group){
            if(channel != incoming){
                channel.writeAndFlush(new TextWebSocketFrame("[" + incoming.remoteAddress() + "]" + msg.text()));
            } else {
                channel.writeAndFlush(new TextWebSocketFrame("[you]" + msg.text()));
            }
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Channel incoming = ctx.channel();

        group.writeAndFlush(new TextWebSocketFrame("[SERVER] - " + incoming.remoteAddress() + "加入"));
        group.add(incoming);
        System.out.println("Client:"+incoming.remoteAddress()+"加入");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Channel incoming = ctx.channel();
        group.writeAndFlush(new TextWebSocketFrame("[SERVER] - " + incoming.remoteAddress() + "离开"));
        System.out.println("Client:"+incoming.remoteAddress()+"离开");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel incoming = ctx.channel();
        System.out.println("Client:"+incoming.remoteAddress()+"在线");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel incoming = ctx.channel();
        System.out.println("Client:"+incoming.remoteAddress()+"掉线");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel incoming = ctx.channel();
        System.out.println("Client:"+incoming.remoteAddress()+"异常");
        cause.printStackTrace();
        ctx.close();
    }
}
