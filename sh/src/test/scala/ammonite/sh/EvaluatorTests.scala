package ammonite.sh

import ammonite.sh.eval.{Compiler, Evaluator, Preprocessor}
import ammonite.sh.{Result, PConfig, PPrint}
import utest._

import scala.collection.{immutable => imm}
import scala.reflect.io.VirtualDirectory

object EvaluatorTests extends TestSuite{
  def check[T: PPrint](t: T, expected: String)(implicit pc: PConfig) = {
    val pprinted = PPrint(t)
    assert(pprinted == expected.trim)
  }
  val tests = TestSuite{
    val preprocess = new Preprocessor{
      override def pprintSignature(ident: String) = s"""
          "$ident" +  ": " +
          ammonite.sh.Shell.typeString($ident)"""

    }
    val dynamicClasspath = new VirtualDirectory("(memory)", None)
    val compiler = new Compiler(dynamicClasspath)
    val eval = new Evaluator(
      Thread.currentThread().getContextClassLoader,
      preprocess.apply,
      compiler.compile
    )

    def check(input: String, expected: String) = {
      val processed = eval.processLine(input)
      val printed = processed.map{case (out, importKeys, imports) => out}
      assert(printed == Result.Success(expected))
      eval.update(processed)
    }
    'simpleExpressions{
      check("1 + 2", "res0: Int = 3")
      check("res0", "res1: Int = 3")
      check("res0 + res1", "res2: Int = 6")
    }
    'vals{
      check("val x = 10", "x: Int = 10")
      check("x", "res1: Int = 10")
      check("val y = x + 1", "y: Int = 11")
      check("x * y", "res3: Int = 110")
    }
    'lazyvals{
      // It actually appears when I ask for it
      check("lazy val x = 'h'", "x: Char = <lazy>")
      check("x", "res1: Char = 'h'")

      // The actual evaluation happens in the correct order
      check("var w = 'l'", "w: Char = 'l'")
      check("lazy val y = {w = 'a'; 'A'}", "y: Char = <lazy>")
      check("lazy val z = {w = 'b'; 'B'}", "z: Char = <lazy>")
      check("z", "res5: Char = 'B'")
      check("y", "res6: Char = 'A'")
      check("w", "res7: Char = 'a'")
    }

    'vars{
      check("var x = 10", "x: Int = 10")
      check("x", "res1: Int = 10")
      check("x = 1", "res2: Unit = ()")
      check("x", "res3: Int = 1")
    }

    'defs{
      check("def sumItAll[T: Numeric](i: Seq[T]): T = {i.sum}", "defined function sumItAll")
      check("sumItAll(Seq(1, 2, 3, 4, 5))", "res1: Int = 15")
      check("sumItAll(Seq(1L, 2L, 3L, 4L, 5L))", "res2: Long = 15L")
    }
    'library{
      check("val x = Iterator.continually(1)", "x: Iterator[Int] = non-empty iterator")
      check("val y = x.take(15)", "y: Iterator[Int] = non-empty iterator")
      check("val z = y.foldLeft(0)(_ + _)", "z: Int = 15")
    }

    'import{
      check("import math.abs", "import math.abs")
      check("abs(-10)", "res1: Int = 10")
    }

    'nesting{
      check("val x = 1", "x: Int = 1")
      check("val x = 2", "x: Int = 2")
      check("x", "res2: Int = 2")
      check("object X{ val Y = 1 }", "defined object X")
      check("object X{ val Y = 2 }", "defined object X")
      check("X.Y", "res5: Int = 2")
    }
  }
}
