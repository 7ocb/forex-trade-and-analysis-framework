package testing.utils.reactions

import org.scalatest.{
  SuiteMixin,
  FlatSpec
}

trait InitAndRunTests extends SuiteMixin { this: FlatSpec =>

  private var _currentTest:Test = null

  trait Test {
    _currentTest = this

    def run()
  }

  abstract override def withFixture(test:NoArgTest) = {
    try {
      super.withFixture(new NoArgTest() {
                          def apply() = {
                            test()
                            if (_currentTest != null) _currentTest.run()
                          }

                          val configMap = test.configMap
                          val name = test.name
                          val scopes = test.scopes
                          val tags = test.tags
                          val text = test.text

                          override def toString() = test.toString()

                        })
    } finally {
      _currentTest = null
    }
  }
}
