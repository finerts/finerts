package junit.kr.ac.korea.agent.data;

public interface ThreadedDataset {
    class UnexpectedCoverageType extends RuntimeException {

    }

    default void addClass(int hash) throws UnexpectedCoverageType {
        throw new UnexpectedCoverageType();
    }

    default void add(int methodHash, int classHash)
            throws UnexpectedCoverageType {
        throw new UnexpectedCoverageType();
    }

    default void addInstanceMethod(int methodHash)
            throws UnexpectedCoverageType {
        throw new UnexpectedCoverageType();
    }

    default void addOverriableMethod(Object src, long key, int extraId) throws UnexpectedCoverageType {
        throw new UnexpectedCoverageType();
    }
}
