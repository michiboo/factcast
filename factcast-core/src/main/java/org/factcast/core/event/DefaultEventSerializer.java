/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.core.event;

import org.factcast.factus.event.EventObject;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class DefaultEventSerializer implements EventSerializer {
    @NonNull
    final ObjectMapper om;

    @SneakyThrows
    @Override
    public <T extends EventObject> T deserialize(@NonNull Class<T> targetClass,
            @NonNull String json) {
        return om.readerFor(targetClass).readValue(json);
    }

    @SneakyThrows
    @Override
    public <T extends EventObject> String serialize(@NonNull T pojo) {
        return om.writeValueAsString(pojo);
    }
}
