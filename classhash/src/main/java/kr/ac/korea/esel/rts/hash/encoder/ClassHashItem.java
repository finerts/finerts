package kr.ac.korea.esel.rts.hash.encoder;

public interface ClassHashItem {
  int classHash();

  String internalClassName();

  Iterable<MethodHashItem> iterateMethods();
  MethodHashItem method(String name, String desc);
}
