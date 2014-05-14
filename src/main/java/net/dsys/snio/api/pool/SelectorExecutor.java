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

package net.dsys.snio.api.pool;

import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Callable;

import net.dsys.commons.impl.future.SettableCallbackFuture;

/**
 * @author Ricardo Padilha
 */
public interface SelectorExecutor {

	<S extends SelectableChannel & NetworkChannel> void bind(S channel, Acceptor acceptor);

	<S extends SelectableChannel & NetworkChannel> void connect(S channel, Processor processor);

	<S extends SelectableChannel & NetworkChannel> void register(S channel, Processor processor);

	void cancelBind(SelectionKey key, SettableCallbackFuture<Void> future, Callable<Void> task);

	void cancelConnect(SelectionKey readKey, SettableCallbackFuture<Void> readFuture, SelectionKey writeKey,
			SettableCallbackFuture<Void> writeFuture);

}