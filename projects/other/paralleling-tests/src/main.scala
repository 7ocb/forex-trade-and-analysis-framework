
import tas.concurrency.RunLoop

import java.io.Serializable

import tas.paralleling.{
  RunnersStarter,
  Controller,
  Action
}

case class Result(a:Int) extends Serializable

object Test extends App {
  println("Test is started")

  val runLoop = new RunLoop

  val controller = new Controller(runLoop)

  RunnersStarter.startRunners("127.0.0.1:9000", Some(2))

  (1 to 10).foreach(tn =>
    controller.submit(new Action[Result] {
                        def run():Result = {
                          println("runner started working for task: " + tn)
                          Thread.sleep(2000)
                          println("runner ended working for task: " + tn)
                          Result(tn + 20)
                        }
                      }, (a:Result) => println("returned value: " + a.a)))


  runLoop()

}
