package com.bwz.study.syslog;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class LogEventBroadcaster {

    public void run(int port) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();

        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new LogEventEncoder());
        bootstrap.bind(port).sync().channel().closeFuture().await();
    }

    public static void main(String[] args) throws Exception{
        //String filePath = "/Users/biwenzhi/java/netty/src/main/resources/test.txt";
        LogEventBroadcaster broadcaster = new LogEventBroadcaster();

        try {
            broadcaster.run(8080);
        } finally {

        }
    }
}
