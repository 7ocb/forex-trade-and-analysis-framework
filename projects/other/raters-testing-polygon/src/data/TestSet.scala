package tas.raters.polygon.data

import java.io.FileInputStream
import java.io.File

import tas.utils.files.lined.LinedFileReader
import tas.raters.polygon.Paths

import tas.output.logger.Logger
import tas.utils.IO
import tas.raters.polygon.rater.Rater

private [data] class TestValidator(paths:Paths, logger:Logger) {

  private val validationMethods =
    List(eachTest(haveNoMissingSourceFiles),
         eachTest(haveSamePointsCountInAllCurves))

  def haveNoMissingSourceFiles(test:Test):Boolean =
    test.order.map(curveId => {
                     val exists = (new File(paths.dataPath,
                                            curveId.stringId)
                                     .exists())

                     if (! exists) {
                       logger.log("Test " + test.id + " refers missing curve " + curveId)
                     }

                     exists
                   })
      .reduce(_ && _)




  def haveSamePointsCountInAllCurves(test:Test):Boolean = {
    val pointsCounts = test.order
      .map(_.stringId)
      .map(new File(paths.dataPath, _))
      .filter(_.exists())
      .map(new Curve(_))
      .map(_.points.size)

    if (pointsCounts == 0) {
      logger.log("Test " + test.id + " refers no curves")
      return false
    }

    val firstCount = pointsCounts.head

    if (firstCount == 0) {
      logger.log("Test " + test.id + " refers empty curves")
      return false
    }

    val differentCount = pointsCounts.exists(_ != firstCount)

    if (differentCount) {
      logger.log("Test " + test.id + " refers curves with different count of points")
      return false
    }

    return true
  }

  def eachTest(oneTestValidator:(Test)=>Boolean):(List[Test])=>Boolean =
    (tests:List[Test]) => tests.map(oneTestValidator).reduce(_ && _)

  def validate(tests:List[Test]):Boolean =
    validationMethods
      .map(_(tests))
      .reduce(_ && _)

}

class TestRunner(paths:Paths, logger:Logger, tests:List[Test]) {

  def executeTest(rater:Rater, test:Test) = {

    val expectedOrder = test.order.map(_.stringId)

    val inputList = expectedOrder.map(new File(paths.dataPath,
                                               _))

    val raterResult = rater.rate(inputList)

    val success = expectedOrder == raterResult

    if (success) {
      logger.log("== Test " + test.id + " succeed.")
    } else {
      logger.log("== Test " + test.id + " failed:")
      logger.log("Expected:")
      expectedOrder.foreach(logger.log(_))
      logger.log("Result:")
      raterResult.foreach(logger.log(_))
    }

    success
  }

  def run(rater:Rater) = {
    val succeed =
      tests
        .map(executeTest(rater, _))
        .count(result => result)

    val count = tests.size

    logger.log("Tests: ", count, ", succeed: ", succeed, ", failed: ", count - succeed)
  }
}

class TestSet(root:File) {

  if ( ! root.exists) throw new RuntimeException("Directory " + root + " does not exist")
  if ( ! root.isDirectory) throw new RuntimeException("File " + root + " is not directory")

  private val paths = new Paths(root)

  private lazy val tests = {
    val testsFiles = paths.testsPath.listFiles()

    testsFiles.map(file =>
      {
        val name = file.getName()

        val contents = IO.withStream(new FileInputStream(file),
                                     LinedFileReader.read)

        new Test(new Id(name),
                 contents.significantLines.map(new Id(_)))
      })
  }

  def executeFor(logger:Logger, rater:Rater) = {
    new TestRunner(paths, logger, tests.toList).run(rater)
  }

  def validate(logger:Logger):Boolean =
    new TestValidator(paths, logger).validate(tests.toList)
}
