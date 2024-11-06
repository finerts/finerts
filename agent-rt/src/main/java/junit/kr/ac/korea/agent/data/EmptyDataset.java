package junit.kr.ac.korea.agent.data;

public class EmptyDataset implements AgentDataset {
    public static final ThreadedDataset EMPTY = new ThreadedDataset() {
        @Override
        public void addClass(int hash) throws UnexpectedCoverageType {
        }

        @Override
        public void add(int methodHash, int classHash)
                throws UnexpectedCoverageType {
        }

        @Override
        public void addInstanceMethod(int methodHash)
                throws UnexpectedCoverageType {
        }

        @Override
        public void addOverriableMethod(Object obj, long key, int extraId) throws UnexpectedCoverageType {
        }
    };

    @Override
    public ThreadedDataset get() {
        return EMPTY;
    }

    @Override
    public void dump() {        
    }
}
