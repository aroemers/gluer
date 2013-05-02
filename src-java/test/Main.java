package test;

import test.modela.SuperSuperA;
import test.modela.SuperA;
import test.modela.A;
import test.modela.SubA;

import java.lang.reflect.Method;


public class Main {

        private SuperSuperA superSuperA;
        private SuperA superA;
        private A a;
        private A twice;
        private SubA subA;

        public Main(final String[] args) throws Exception {
                System.out.println("<test.Main> Starting application...");
                for (final String arg : args) {
                        System.out.println("<test.Main> Calling method "+ arg +"() ...");
                        final Object fieldObject =
                                getClass().getDeclaredField(arg.split("\\.")[0]).get(this);
                        final Method objMethod =
                                fieldObject.getClass().getMethod(arg.split("\\.")[1], new Class<?>[0]);
                        objMethod.invoke(fieldObject);
                }
                System.out.println("<test.Main> Exiting application.");
        }


        public static void main(final String[] args) throws Exception {
                new Main(args);
        }

}
