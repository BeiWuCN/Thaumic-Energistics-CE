package thaumicenergistics_ce.blockentity.assembler;

/**
 * The two halves a craft is split across, each built when it is first asked for: the job that takes and
 * prices one, and the runner that sees it through. Split out of {@link BlockEntityArcaneAssembler}, which
 * keeps one accessor for each so the rest of the package reaches them exactly as before.
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
