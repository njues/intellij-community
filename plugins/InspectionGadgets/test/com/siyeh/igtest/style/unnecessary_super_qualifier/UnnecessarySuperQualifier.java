package com.siyeh.igtest.style.unnecessary_super_qualifier;

public class UnnecessarySuperQualifier {

    private static class Base<T> {
        int field;

        void test(T v) {
        }
    }

    private static class Derived extends Base<String> {
        int field;

        void test(String v) {
            super.test(v);   // <-----
            System.out.println(super.field);
        }
    }

    private static class Up extends Base<String> {
        void foo() {
            <warning descr="Qualifier 'super' is unnecessary in this context">super</warning>.test("asfd");
            System.out.println(<warning descr="Qualifier 'super' is unnecessary in this context">super</warning>.field);
        }
    }

    class Father {
        protected int prot;
    }

    class Son extends Father {
        public void context(int prot) {
            super.prot = prot;
        }
    }

    public static abstract class Parent {

        private final int f = 0;

        private void a() {}

        static class Child extends Parent {

            @Override
            public String toString() {
                super.a();
                return String.valueOf(super.f);
            }
        }
    }
    private class OtherChild extends Parent {

        public String toString() {
            super.a();
            return String.valueOf(super.f);
        }
    }

    public static abstract class Uncle {

        final int f = 0;

        void a() {}

        static class Nephew extends Uncle {

            @Override
            public String toString() {
                <warning descr="Qualifier 'super' is unnecessary in this context">super</warning>.a();
                return String.valueOf(<warning descr="Qualifier 'super' is unnecessary in this context">super</warning>.f);
            }
        }
    }
    private class Niece extends Uncle {

        public String toString() {
            <warning descr="Qualifier 'super' is unnecessary in this context">super</warning>.a();
            return String.valueOf(<warning descr="Qualifier 'super' is unnecessary in this context">super</warning>.f);
        }
    }
}