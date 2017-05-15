import org.jetbrains.annotations.*;
import java.util.*;

abstract class ObviousNullCheck {
  abstract String getFoo();

  abstract String getBar();

  void test() {
    assertNotNull(<warning descr="Useless null-check: a value of primitive type is never null">5 + 6</warning>);

    Objects.requireNonNull(null);
    Objects.requireNonNull(<warning descr="Useless null-check: literal is never null">"xyz"</warning>, "xyz");
    Objects.requireNonNull((<warning descr="Useless null-check: concatenation is never null">getFoo() + getBar()</warning>));
    Objects.requireNonNull(<warning descr="Useless null-check: newly created object is never null">new ArrayList()</warning>, "new returned null");

    String s = Objects.requireNonNull(<warning descr="Useless null-check: literal is never null">" x "</warning>);
    String s1 = trim(" x ");
  }

  @Contract(value="null -> fail", pure=true)
  String trim(String s) {
    return s.trim();
  }

  @Contract(value="null -> fail", pure=true)
  static void assertNotNull(Object obj) {
    if(obj == null) throw new NullPointerException();
  }
}