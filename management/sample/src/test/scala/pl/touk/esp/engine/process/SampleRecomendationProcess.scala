package pl.touk.esp.engine.process

import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.management.sample.DemoProcessConfigCreator
import pl.touk.esp.engine.spel

import scala.concurrent.Future

class SampleRecomendationProcess extends FlatSpec with Matchers {

  import spel.Implicits._
  import scala.concurrent.ExecutionContext.Implicits.global

  val creator = new DemoProcessConfigCreator
  val env = StreamExecutionEnvironment.createLocalEnvironment()

  it should "serialize and run" in {
    val process =
      EspProcessBuilder
        .id("sample")
        .parallelism(1)
        .exceptionHandler("topic" -> "errors")
        .source("start", "PageVisits", "ratePerMinute" -> "3")
        .sink("end", "#input", "Recommend")

    val config = ConfigFactory.load()

    FlinkProcessRegistrar(creator, config).register(env, process)

    Future {
      env.execute("sample")
    }.failed.foreach(_.printStackTrace())
    Thread.sleep(2000)
  }
}
