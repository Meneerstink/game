"""Build Claude-owned logic patches as text. Original source files remain byte-identical."""
from pathlib import Path
from make_proposals import ROOT, DOC, PLUGIN, patch


def main():
    out=DOC/'voorstel';out.mkdir(exist_ok=True)
    path=PLUGIN+'content/quests/tab/DropSources.kt';old=(ROOT/path).read_text(encoding='utf-8');new=old
    def change(a,b):
        nonlocal new
        if a not in new:raise ValueError('Proposal anchor absent: '+a)
        new=new.replace(a,b)
    change('rate: Double = DropTableFactory.killRate(player)): View {','rate: Double = DropViewer.sourceRate(player, source)): View {')
    change('private val indexes = java.util.concurrent.ConcurrentHashMap<Double, ItemIndex>()','''// Snapshot the bracket and Fortuity effect separately; neither may leak into the other player's search.
    private val indexes = java.util.concurrent.ConcurrentHashMap<String, ItemIndex>()
    private fun bracketRate(player: Player) = gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier.of(player).dropRate
    private fun indexKey(rate: Double, bonus: Double) = "$rate|$bonus"
    private fun indexRate(player: Player, source: Source, rate: Double, bonus: Double): Double {
        val npc = when (source.kind) {
            Kind.NPC -> source.tableNpc
            Kind.BREACH, Kind.GUARDIAN, Kind.JAD -> source.modelNpc
            else -> null
        }
        val boss = npc == null || gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.sourceOf(player.world, npc) ==
            gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.Source.BOSS
        return rate + if (boss) bonus else 0.0
    }''')
    change('if (warming || indexes.size >= RATES.size) return','''val bonus = gg.rsmod.plugins.content.mechanics.deadman.DeadmanTrinkets.bonus(player)
        if (warming || RATES.all { indexes.containsKey(indexKey(it, bonus)) }) return''')
    change('val own = DropTableFactory.killRate(player)','val own = bracketRate(player)')
    change('if (indexes.containsKey(rate)) continue','if (indexes.containsKey(indexKey(rate, bonus))) continue')
    change('itemChances(view(player, s, rate))','itemChances(view(player, s, indexRate(player, s, rate, bonus)))')
    change('indexes.putIfAbsent(rate, ItemIndex(names, bySource))','indexes.putIfAbsent(indexKey(rate, bonus), ItemIndex(names, bySource))')
    change('''        player.world.queue {
            val sources = all(player)''','''        player.world.queue {
            try {
            val sources = all(player)''')
    change('''                indexes.putIfAbsent(indexKey(rate, bonus), ItemIndex(names, bySource))
            }
        }
    }''','''                indexes.putIfAbsent(indexKey(rate, bonus), ItemIndex(names, bySource))
            }
            } finally { warming = false }
        }
    }''')
    change('fun itemIndex(player: Player, rate: Double = DropTableFactory.killRate(player)): ItemIndex =\n        (indexes[rate]',
           '''fun itemIndex(player: Player, rate: Double = bracketRate(player)): ItemIndex {
        val bonus = gg.rsmod.plugins.content.mechanics.deadman.DeadmanTrinkets.bonus(player)
        val key = indexKey(rate, bonus)
        return (indexes[key]''')
    change('indexes[rate] ?: run {','indexes[key] ?: run {')
    change('indexes[rate] = it','indexes[key] = it')
    change('}).let { personalWildernessIndex(player, it, rate) }','''}).let { personalWildernessIndex(player, it, rate) }
    }''')
    # Personal NPC variants must refresh with the same source-aware rate as the base index.
    change('itemChances(view(player, source, rate)).forEach',
           'itemChances(view(player, source, indexRate(player, source, rate, gg.rsmod.plugins.content.mechanics.deadman.DeadmanTrinkets.bonus(player)))).forEach')
    change('val item = BreachIds.ARCHAIC_EMBLEM_TIER_5\n        if (item <= 0)',
           'val item = BreachIds.ARCHAIC_EMBLEM_TIER_5\n        if (gg.rsmod.plugins.content.mechanics.deadman.BreachRetirement.enabled || item <= 0)')
    patch(out,'04-DropSources.kt.patch',path,old,new)
    # Store patched copies only under the local build directory for an isolated compile if needed.
    staged=ROOT/'build/dmm2/proposal-sources';staged.mkdir(parents=True,exist_ok=True)
    (staged/'DropSources.kt').write_text(new,encoding='utf-8')
    path=PLUGIN+'content/quests/tab/CollectionLogPages.kt';old=(ROOT/path).read_text(encoding='utf-8');new=old
    a='listOfNotNull(BreachIds.ARCHAIC_EMBLEM_TIER_5.takeIf { it > 0 })'
    b='listOfNotNull(BreachIds.ARCHAIC_EMBLEM_TIER_5.takeIf { it > 0 && !gg.rsmod.plugins.content.mechanics.deadman.BreachRetirement.enabled })'
    assert a in new;new=new.replace(a,b)
    anchor='        // ---- Other: every pet.\n'
    assert anchor in new
    new=new.replace(anchor,'''        // Fire ruby is an Infernal Mage tertiary, not a Deadman chest/reward-store item.
        // Source keys come from the existing real drop-table census; no extra kill counter invented.
        gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.ItemId.FIRE_RUBY.local?.let { ruby ->
            val mages = sources.filter { s -> s.npcs.any { DropSources.npcName(world, it).equals("Infernal Mage", true) } }
            if (mages.isNotEmpty()) pages += Page("fire_ruby", Category.OTHER, "Fire ruby", mages.map { it.key }, listOf(ruby))
        }
'''+anchor)
    patch(out,'05-CollectionLogPages.kt.patch',path,old,new)
    (staged/'CollectionLogPages.kt').write_text(new,encoding='utf-8')
    print('Two logic proposals written; reserved DropSources/CollectionLogPages unchanged')


if __name__=='__main__':main()
