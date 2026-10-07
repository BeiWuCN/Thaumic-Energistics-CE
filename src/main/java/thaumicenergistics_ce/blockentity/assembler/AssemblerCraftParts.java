package thaumicenergistics_ce.blockentity.assembler;

/**
 * 一次合成被拆成的两半，各自在首次被请求时构建：接收并定价的任务对象，
 * 以及把它执行到底的运行器。从 {@link BlockEntityArcaneAssembler} 拆出，后者为
 * 二者各留一个访问器，使包内其余部分拿到的接口与之前完全一致。
 */
final class AssemblerCraftParts {

    private final BlockEntityArcaneAssembler owner;

    private AssemblerCraftJob job;
    private AssemblerCraftRunner runner;

    AssemblerCraftParts(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    AssemblerCraftJob job() {
        if (job == null) {
            job = new AssemblerCraftJob(owner);
        }
        return job;
    }

    AssemblerCraftRunner runner() {
        if (runner == null) {
            runner = new AssemblerCraftRunner(owner);
        }
        return runner;
    }
}
