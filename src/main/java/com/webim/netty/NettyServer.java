package com.webim.netty;

import com.webim.netty.handler.ChatHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Netty WebSocket 服务器
 * 负责监听端口并管理 WebSocket 的连接生命周期
 */
@Slf4j
@Component
public class NettyServer {

    /**
     * WebSocket 服务端口，从配置文件读取
     */
    @Value("${netty.websocket.port:8888}")
    private int port;

    /**
     * WebSocket 访问路径，例如 /ws
     */
    @Value("${netty.websocket.path:/ws}")
    private String path;

    // Netty 核心线程池：bossGroup 用于接受新连接，workerGroup 用于处理已建立连接的 I/O 业务
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private final ChatHandler chatHandler;

    public NettyServer(ChatHandler chatHandler) {
        this.chatHandler = chatHandler;
    }

    /**
     * 在 Spring 容器启动并初始化 Bean 后，自动启动 Netty 服务
     */
    @PostConstruct
    public void start() {
        // 在新线程中启动，以免阻塞 Spring Boot 的主引导过程
        new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                // 1. HttpServerCodec: 将请求/响应编码解码为 HTTP 报文
                                ch.pipeline().addLast(new HttpServerCodec());
                                // 2. IdleStateHandler: 心跳检测处理器，180秒未收到客户端数据将触发 READER_IDLE 事件
                                ch.pipeline().addLast(new io.netty.handler.timeout.IdleStateHandler(180, 0, 0));
                                // 3. ChunkedWriteHandler: 方便向客户端发送大数据流（如大文件文件块）
                                ch.pipeline().addLast(new ChunkedWriteHandler());
                                // 4. HttpObjectAggregator: 将 HTTP 消息的多个部分（如 Header/Body）聚合成一个完整的请求
                                ch.pipeline().addLast(new HttpObjectAggregator(65536));
                                // 5. WebSocketServerProtocolHandler: 核心处理器，处理 WebSocket 握手及 Ping/Pong/Close 帧
                                ch.pipeline().addLast(new WebSocketServerProtocolHandler(path));
                                // 6. ChatHandler: 自定义业务逻辑处理器，处理认证、转发等 IM 核心功能
                                ch.pipeline().addLast(chatHandler);
                            }
                        });

                log.info("Netty WebSocket 服务器已准备就绪，正在监听端口: {}", port);
                ChannelFuture f = b.bind(port).sync();
                f.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("Netty 服务器在运行过程中发生中断异常", e);
                Thread.currentThread().interrupt();
            } finally {
                stop();
            }
        }).start();
    }

    /**
     * Spring 容器关闭前执行清理，优雅关闭 Netty 线程池
     */
    @PreDestroy
    public void stop() {
        log.info("正在优雅地停机 Netty 服务器...");
        if (bossGroup != null)
            bossGroup.shutdownGracefully();
        if (workerGroup != null)
            workerGroup.shutdownGracefully();
    }
}
