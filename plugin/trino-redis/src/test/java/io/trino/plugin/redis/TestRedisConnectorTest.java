/*
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
package io.trino.plugin.redis;

import com.google.common.collect.ImmutableMap;
import io.trino.plugin.redis.util.RedisServer;
import io.trino.testing.BaseConnectorTest;
import io.trino.testing.QueryRunner;
import org.testng.annotations.AfterClass;

import static io.trino.plugin.redis.RedisQueryRunner.createRedisQueryRunner;

public class TestRedisConnectorTest
        extends BaseConnectorTest
{
    private RedisServer redisServer;

    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        redisServer = new RedisServer();
        return createRedisQueryRunner(redisServer, ImmutableMap.of(), "string", REQUIRED_TPCH_TABLES);
    }

    @AfterClass(alwaysRun = true)
    public void destroy()
    {
        redisServer.close();
    }

    @Override
    protected boolean supportsCreateSchema()
    {
        return false;
    }

    @Override
    protected boolean supportsCreateTable()
    {
        return false;
    }

    @Override
    protected boolean supportsInsert()
    {
        return false;
    }

    @Override
    protected boolean supportsDelete()
    {
        return false;
    }

    @Override
    protected boolean supportsViews()
    {
        return false;
    }

    @Override
    protected boolean supportsArrays()
    {
        return false;
    }

    @Override
    protected boolean supportsCommentOnTable()
    {
        return false;
    }

    @Override
    protected boolean supportsCommentOnColumn()
    {
        return false;
    }
}
