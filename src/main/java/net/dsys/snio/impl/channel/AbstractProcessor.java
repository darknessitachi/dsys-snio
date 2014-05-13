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

package net.dsys.snio.impl.channel;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import net.dsys.commons.impl.future.MergingFuture;
import net.dsys.commons.impl.future.SettableFuture;
import net.dsys.snio.api.buffer.MessageBufferConsumer;
import net.dsys.snio.api.buffer.MessageBufferProducer;
import net.dsys.snio.api.buffer.MessageBufferProvider;
import net.dsys.snio.api.pool.KeyProcessor;
import net.dsys.snio.api.pool.SelectionType;
import net.dsys.snio.api.pool.SelectorExecutor;
import net.dsys.snio.api.pool.SelectorThread;

/**
 * @author Ricardo Padilha
 */
abstract class AbstractProcessor<T> implements KeyProcessor<T> {

	private final SettableFuture<Void> connectReadFuture;
	private final SettableFuture<Void> connectWriteFuture;
	private final MergingFuture<Void> connectFuture;
	private final SettableFuture<Void> closeReadFuture;
	private final SettableFuture<Void> closeWriteFuture;
	private final SettableFuture<Void> shutdownFuture;
	private final MergingFuture<Void> closeFuture;

	private final MessageBufferProvider<T> provider;
	private final MessageBufferProducer<T> appOut;
	private final MessageBufferConsumer<T> chnIn;
	private final MessageBufferProducer<T> chnOut;
	private final MessageBufferConsumer<T> appIn;

	private SelectorThread thread;
	private SelectionKey readKey;
	private SelectionKey writeKey;

	protected AbstractProcessor(final MessageBufferProvider<T> provider) {
		if (provider == null) {
			throw new NullPointerException("provider == null");
		}
		this.connectReadFuture = new SettableFuture<>();
		this.connectWriteFuture = new SettableFuture<>();
		this.connectFuture = MergingFuture.<Void>builder()
				.add(connectReadFuture).add(connectWriteFuture).build();

		this.closeReadFuture = new SettableFuture<>();
		this.closeWriteFuture = new SettableFuture<>();
		this.shutdownFuture = new SettableFuture<>();
		this.closeFuture = MergingFuture.<Void>builder()
				.add(shutdownFuture).add(closeReadFuture).add(closeWriteFuture).build();

		this.provider = provider;
		this.appOut = provider.getAppOut(this);
		this.chnIn = provider.getChannelIn();
		this.chnOut = provider.getChannelOut();
		this.appIn = provider.getAppIn();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final Future<Void> getConnectionFuture() {
		return connectFuture;
	}

	protected final SettableFuture<Void> getConnectReadFuture() {
		return connectReadFuture;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void registered(final SelectorThread thread, final SelectionKey key, final SelectionType type) {
		switch (type) {
			case OP_READ: {
				this.readKey = key;
				readRegistered(key);
				if (connectReadFuture.isDone()) {
					throw new AssertionError("connectFuture.isDone() while register");
				}
				connectReadFuture.success(null);
				break;
			}
			case OP_WRITE: {
				this.thread = thread;
				this.writeKey = key;
				writeRegistered(key);
				if (connectWriteFuture.isDone()) {
					throw new AssertionError("connectFuture.isDone() while register");
				}
				connectWriteFuture.success(null);
				break;
			}
			case OP_CONNECT: {
				this.readKey = key;
				break;
			}
			default: {
				throw new AssertionError("Unsupported SelectionType registered: " + String.valueOf(type));
			}
		}
	}

	protected abstract void readRegistered(SelectionKey key);
	protected abstract void writeRegistered(SelectionKey key);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void wakeupWriter() {
		if (writeKey != null && writeKey.isValid()) {
			thread.enableKey(writeKey);
		}
	}

	/**
	 * Only called from within the selector thread.
	 */
	protected final void disableWriter() {
		writeKey.interestOps(writeKey.interestOps() & ~SelectionKey.OP_WRITE);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final MessageBufferConsumer<T> getInputBuffer() {
		return appIn;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final MessageBufferProducer<T> getOutputBuffer() {
		return appOut;
	}

	protected final MessageBufferConsumer<T> getChannelInput() {
		return chnIn;
	}

	protected final MessageBufferProducer<T> getChannelOutput() {
		return chnOut;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void close(final SelectorExecutor executor, final Callable<Void> closeTask) {
		final Callable<Void> task = new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				shutdown(executor);
				closeTask.call();
				return null;
			}
		};
		// tell subclasses to shutdown
		shutdown(shutdownFuture, task);
	}

	final void shutdown(final SelectorExecutor executor) {
		provider.close();
		executor.cancelConnect(readKey, closeReadFuture, writeKey, closeWriteFuture);
	}

	/**
	 * Subclasses need to start shutdown upon call, then once done run the task,
	 * and set the output of the future.
	 */
	protected abstract void shutdown(SettableFuture<Void> future, Callable<Void> task);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final Future<Void> getCloseFuture() {
		return closeFuture;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final String toString() {
		return String.format("%s{read=%s, write=%s, channel=%s, local=%s, remote=%s}",
			this.getClass().getSimpleName(),
			Boolean.valueOf(readKey != null), Boolean.valueOf(writeKey != null),
			getChannel(), getLocal(), getRemote());
	}

	private SelectableChannel getChannel() {
		if (readKey != null) {
			return readKey.channel();
		}
		if (writeKey != null) {
			return writeKey.channel();
		}
		return null;
	}

	private SocketAddress getLocal() {
		final SelectableChannel channel = getChannel();
		if (channel == null) {
			return null;
		}
		try {
			return ((SocketChannel) channel).getLocalAddress();
		} catch (ClassCastException | IOException e) {
			return null;
		}
	}

	private Object getRemote() {
		final SelectableChannel channel = getChannel();
		if (channel == null) {
			return null;
		}
		try {
			return ((SocketChannel) channel).getRemoteAddress();
		} catch (ClassCastException | IOException e) {
			return null;
		}
	}
}
