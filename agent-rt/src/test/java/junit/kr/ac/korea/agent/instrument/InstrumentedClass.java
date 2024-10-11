// package junit.kr.ac.korea.agent.instrument;

// public class InstrumentedClass {

//     static Object staticField = new Object();
//     Object instanceField = new Object();

//     public InstrumentedClass() {
//     }

//     private InstrumentedClass(int i) {

//     }

//     private static void privateStaticMethod() {

//     }

//     static void staticMethod() {

//     }

//     void testStaticFieldAccess() {
//         Object o = TargetClass.staticField; // access static field
//         TargetClass.staticField = null; // put into static field
//     }

//     void testInstanceFieldAccess() {
//         TargetClass c = new TargetClass();
//         c.instanceField = null; // access instance field
//         Object o = c.instanceField;    // put into instance field
//     }

//     void testOwnField() {
//         Object o = staticField;
//         staticField = null;

//         instanceField = null;
//     }

//     private void privateMethod() {

//     }
// }
