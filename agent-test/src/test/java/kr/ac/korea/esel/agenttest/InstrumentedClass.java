package kr.ac.korea.esel.agenttest;

public class InstrumentedClass {

    static Object staticField = new Object();
    Object instanceField = new Object();

    public static void staticMethod() {
    }

    public void testStaticFieldAccess() {
        Object o = TargetClass.staticField; // access static field
        TargetClass.staticField = null; // put into static field
    }

    public void testInstanceFieldAccess() {
        TargetClass c = new TargetClass();
        c.instanceField = null; // access instance field
        Object o = c.instanceField;    // put into instance field
    }

    public void testOwnField() {
        Object o = staticField;
        staticField = null;

        instanceField = null;
    }
}
