/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.elasticsearch.akkahelpers

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Terminated}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.sumologic.elasticsearch.akkahelpers.BulkIndexerActor.{BulkSession, CreateRequest, DocumentIndexed, ForceFlush}
import com.sumologic.elasticsearch.restlastic.{RestlasticSearchClient, RestlasticSearchClient6}
import com.sumologic.elasticsearch.restlastic.RestlasticSearchClient.ReturnTypes.BulkItem
import com.sumologic.elasticsearch.restlastic.dsl.Dsl._
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

@RunWith(classOf[JUnitRunner])
class BulkIndexerActorTest extends TestKit(ActorSystem("TestSystem")) with WordSpecLike with Matchers
with BeforeAndAfterAll with BeforeAndAfterEach with MockitoSugar with ImplicitSender with Eventually {

  val executionContext = scala.concurrent.ExecutionContext.Implicits.global
  var indexerActor: TestActorRef[BulkIndexerActor] = _
  var mockEs = mock[RestlasticSearchClient]
  var flushTimeoutMs = 100000L
  var maxMessages = 100000

  override def beforeEach(): Unit = {
    mockEs = mock[RestlasticSearchClient]
    when(mockEs.indexExecutionCtx).thenReturn(executionContext)
    def timeout() = Duration(flushTimeoutMs, TimeUnit.MILLISECONDS)
    def max() = maxMessages
    val config = BulkConfig(timeout, max)
    indexerActor = TestActorRef[BulkIndexerActor](BulkIndexerActor.props(mockEs, config))

  }

  override def afterAll(): Unit = {
    val terminationFuture: Future[Terminated] = system.terminate()
    Await.result(terminationFuture, 5.seconds)
  }

  "BulkIndexerActor" should {
    "flush every message when set to 1" in {
      maxMessages = 1
      when(mockEs.bulkIndex(any())).thenReturn(Future.successful(Seq(BulkItem("index","type", "_id", 201, None))))
      val sess = BulkSession.create()
      indexerActor ! CreateRequest(sess, Index("i"), Type("tpe"), Document("id", Map("k" -> "v")))
      eventually {
        mockEs.bulkIndex(any())
      }
      val msg = expectMsgType[DocumentIndexed]
      msg.sessionId should be(sess)
    }

    "not flush when set to 2" in {
      maxMessages = 2
      indexerActor ! CreateRequest(BulkSession.create(), Index("i"), Type("tpe"), Document("id", Map("k" -> "v")))
      verify(mockEs, times(0)).bulkIndex(any())
    }

    "not flush when there are no messages" in {
      indexerActor ! ForceFlush
      verify(mockEs, times(0)).bulkIndex(any())
    }
  }


}


