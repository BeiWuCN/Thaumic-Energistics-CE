/**
 * The only place in Thaumic Energistics that names Thaumaturge's own classes.
 *
 * <p>Thaumaturge is an addon API still in motion. Between 0.4.4 and 0.4.7 it deleted the whole
 * reservation-based vis relay contract, replaced the arcane crafting transaction's {@code commit}
 * with {@code craft}, and moved members around inside {@code content}. Every one of those breaks
 * arrived here as compile errors scattered across a dozen files. The rule from now on is that a
 * Thaumaturge rename or signature change is repaired <em>inside this package only</em>: callers talk
 * to the {@code Tc*} facades, never to Thaumaturge.
 *
 * <p>Two kinds of reference are deliberately left at the call site instead of being wrapped:
 *
 * <ul>
 *   <li><b>Types that flow through this mod's own signatures.</b> An {@code IAspect}, an
 *       {@code AspectList} or an {@code IEssentiaStorage} is the vocabulary both mods speak. A local
 *       twin for each would build a second type system and force a conversion at every boundary,
 *       while buying nothing: those contracts are the stable half of Thaumaturge.
 *   <li><b>Interfaces this mod implements as an extension point.</b> A vis interface part <em>is</em>
 *       an {@code IVisRelaySource}; a focus item <em>is</em> an {@code ItemFocus}. There the type is
 *       the extension mechanism, and hiding it would mean not implementing it.
 * </ul>
 *
 * <p>Everything else goes through a facade here: every lookup in {@code registry}, every class under
 * {@code content}, the JEI plugin, and every static helper call. When the next Thaumaturge update
 * lands, the compile errors should all point at this directory.
 */
package thaumicenergistics_ce.compat.thaumaturge;
