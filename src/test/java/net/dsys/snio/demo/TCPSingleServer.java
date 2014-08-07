/**
 * Copyright 2014 Ricardo Padilha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dsys.snio.demo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import net.dsys.commons.impl.lang.ByteBufferFactory;
import net.dsys.snio.api.buffer.MessageBufferConsumer;
import net.dsys.snio.api.channel.MessageServerChannel;
import net.dsys.snio.api.handler.MessageHandler;
import net.dsys.snio.api.pool.SelectorPool;
import net.dsys.snio.impl.buffer.RingBufferProvider;
import net.dsys.snio.impl.channel.MessageServerChannels;
import net.dsys.snio.impl.channel.builder.ChannelConfig;
import net.dsys.snio.impl.channel.builder.ServerConfig;
import net.dsys.snio.impl.handler.MessageHandlers;
import net.dsys.snio.impl.pool.SelectorPools;

/**
 * Echo server using TCP. This implementation uses a single processor to handle
 * all incoming messages.
 * 
 * @author Ricardo Padilha
 */
public final class TCPSingleServer {

	private TCPSingleServer() {
		return;
	}

	public static void main(final String[] args) throws IOException, InterruptedException, ExecutionException {
		final int threads = Integer.parseInt(getArg("threads", "1", args));
		final int buffers = Integer.parseInt(getArg("buffers", "256", args));
		final int length = Integer.parseInt(getArg("length", "1024", args));
		final int port = Integer.parseInt(getArg("port", "12345", args));

		final SelectorPool pool = SelectorPools.open("server", threads);
		final MessageBufferConsumer<ByteBuffer> input = RingBufferProvider.createConsumer(buffers,
				new ByteBufferFactory(length));
		final ChannelConfig<ByteBuffer> common = new ChannelConfig<ByteBuffer>()
				.setPool(pool)
				.setBufferCapacity(buffers)
				.useSingleInputBuffer(input)
				.useRingBuffer();
		final ServerConfig server = new ServerConfig()
				.setMessageLength(length);
		final MessageServerChannel<ByteBuffer> channel = MessageServerChannels.openTCPServerChannel(common, server);

		final MessageHandler<ByteBuffer> handler = MessageHandlers.buildHandler()
				//.useDecoupledProcessing(length)
				.useSingleConsumer(new EchoServer())
				.build();

		channel.onAccept(handler.getAcceptListener());
		channel.bind(new InetSocketAddress(port));
		channel.getBindFuture().get();

		pool.getCloseFuture().get();
	}

	private static String getArg(final String name, final String defaultValue, final String[] args) {
		if (args == null || name == null) {
			return defaultValue;
		}
		final String key = "--" + name;
		final int k = args.length - 1;
		for (int i = 0; i < k; i++) {
			if (key.equals(args[i])) {
				return args[i + 1];
			}
		}
		return defaultValue;
	}

}
