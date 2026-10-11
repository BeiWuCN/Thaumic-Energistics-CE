package thaumicenergistics_ce.blockentity.assembler;

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
