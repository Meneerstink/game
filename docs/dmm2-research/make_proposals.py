"""Write review patches ONLY. Never edits Claude's reserved source/config files or applies a patch."""
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import re
import yaml
from dmm2_ids_from_apply import mappings, PARENTS

ROOT = Path(__file__).resolve().parents[2]
DOC = Path(__file__).resolve().parent
PLUGIN = 'game/plugins/src/main/kotlin/gg/rsmod/plugins/'

def load_yaml(text): return yaml.load(text, Loader=yaml.CSafeLoader)


def patch(out, name, path, old, new):
    if old == new: raise ValueError(f'Empty proposal {name}')
    text = ''.join(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='a/'+path,tofile='b/'+path))
    (out/name).write_text(text,encoding='utf-8',newline='\n')


def wiki_bonuses():
    """Use exact published numeric bonuses, including state-specific fields; never scale a base item."""
    fields = dict(astab='attack_stab',aslash='attack_slash',acrush='attack_crush',amagic='attack_magic',arange='attack_ranged',
                  dstab='defence_stab',dslash='defence_slash',dcrush='defence_crush',dmagic='defence_magic',drange='defence_ranged',
                  str='melee_strength',rstr='ranged_strength',mdmg='magic_damage',prayer='prayer')
    out={}
    for file in (DOC/'wiki').glob('Corrupted*.wiki.txt'):
        text=file.read_text(encoding='utf-8'); match=re.search(r'\{\{Infobox Bonuses\s*\n(.*?)\n}}',text,re.S)
        if not match: raise ValueError(f'No bonus infobox in {file}')
        values=dict(re.findall(r'^\|\s*(\w+)\s*=\s*([^\r\n]+)',match[1],re.M))
        for version,source in re.findall(r'^\|id(\d*)\s*=\s*(\d+)\s*$',text,re.M):
            b={}
            for wiki,local in fields.items():
                val=values.get(wiki+version,values.get(wiki))
                if val is None: raise ValueError(f'Missing exact {wiki}/{version} in {file}')
                if not re.fullmatch(r'[+-]?\d+(?:\.\d+)?%?',val.strip()):raise ValueError(f'Unresolved bonus: {val}')
                b[local]=float(val.rstrip('%')) if local=='magic_damage' else int(val)
            out[int(source)]=b
    return out


def replace_yaml_entries(old, entries, plan):
    byid={e['id']:e for e in load_yaml(old)}
    conflicts=[]
    for e in entries:
        existing=byid.get(e['id'])
        if existing and existing.get('name','').lower()!=e['name'].lower():
            conflicts.append(dict(id=e['id'],existing=existing.get('name'),proposed=e['name']))
    if conflicts and not plan:raise ValueError(f'Local ID collision with existing metadata: {conflicts}')
    new=old
    for entry in entries:
        block=yaml.safe_dump([entry],sort_keys=False,allow_unicode=True,width=140)
        pattern=rf'(?ms)^- id: {entry["id"]}\s*\n.*?(?=^- id: |\Z)'
        if re.search(pattern,new):new=re.sub(pattern,lambda _:block,new,count=1)
        else:new=new.rstrip()+'\n'+block
    return new,conflicts


def propose(out, table, native_yml, plan):
    out=out.resolve()
    if not out.is_relative_to(DOC):raise ValueError('Proposal outputs must stay under docs/dmm2-research')
    out.mkdir(parents=True,exist_ok=True)
    metadata=load_yaml(native_yml.read_text(encoding='utf-8'))
    source_for={local:source for (kind,source),local in table.items() if kind=='item'}
    metadata=[e for e in metadata if e['id'] in source_for]
    if not set(PARENTS).issubset({source_for[e['id']] for e in metadata}):raise ValueError('Missing parent metadata')
    original=(ROOT/'data/cfg/items.yml').read_text(encoding='utf-8')
    existing=load_yaml(original)
    byname={e['name'].lower():e for e in existing if 'name' in e and e.get('equipment')}
    source=json.loads((DOC/'cache2686-items.json').read_text(encoding='utf-8'))
    aliases={28531:'voidwaker',28534:'dragon claws',28537:'armadyl godsword',28540:'twisted bow',
             28547:"tumeken's shadow",28549:"tumeken's shadow (uncharged)",
             29599:'dark bow',29602:'volatile nightmare staff',22331:'mithril scimitar',22333:'shortbow',22335:'staff',
             29605:'armadyl godsword',29607:'voidwaker',29609:'volatile nightmare staff',29611:'dark bow',
             29613:'imbued zamorak cape',29615:'imbued guthix cape',29617:'imbued saradomin cape',
             33021:'bow of faerdhinen (c)',33023:'crystal body',33025:'crystal body (inactive)',33027:'crystal legs',
             33029:'crystal legs (inactive)',33031:'crystal helm',33033:'crystal helm (inactive)',
             33035:'toxic staff (uncharged)',33036:'toxic staff of the dead',33041:'voidwaker'}
    bonuses=wiki_bonuses(); warnings=[]
    # In PLAN there are no inserted worn records yet. Native --yml-only ranks see the unchanged snapshot.
    # Every parent is freshly allocated above the snapshot's item max; add each earlier pending wearable once.
    pending=0
    for e in sorted(metadata,key=lambda v:v['id']):
        src=source_for[e['id']]; equip=e.get('equipment')
        if equip:
            if plan:equip['appearance_id']+=pending;pending+=1
            alias=aliases.get(src)
            if alias:
                base=byname.get(alias)
                if not base or not base.get('equipment'):raise ValueError(f'No proven base metadata for {src}: {alias}')
                for key in ['weapon_type','attack_audio']:
                    equip[key]=base['equipment'][key]
            if src in (28543,28545):
                equip['weapon_type']=22  # Existing WeaponType.SCYTHE; dedicated attack/block route in CombatConfigs.
                warnings.append(dict(source=src,field='attack_audio',status='Native -1 retained; Claude must prove/install AV before release'))
            if src in bonuses:
                for key,value in bonuses[src].items():
                    if equip.get(key)!=value:warnings.append(dict(source=src,field=key,cache2686=equip.get(key),wiki=value))
                    equip[key]=value
            # Explicit cache2686/gameval speed, resolving corrupted dark-bow wiki infobox conflict.
            params=source[str(src)]['params']
            if '14' in params:equip['attack_speed']=params['14']
        if '98' in source[str(src)]['fields']:
            e['examine']='Swap this note at any bank for the equivalent item.'
        if src in (28543,28545,28546):
            e['name']='Corrupted Scythe of Vitur'+(' (uncharged)' if src in (28545,28546) else '')
    body,conflicts=replace_yaml_entries(original,metadata,plan)
    suffix='.PLAN-ONLY.patch' if plan else '.patch'
    patch(out,'01-items.yml'+suffix,'data/cfg/items.yml',original,body)
    path=PLUGIN+'api/cfg/Items.kt'; old=(ROOT/path).read_text(encoding='utf-8')
    constants=[]
    metadata_by_id={e['id']:e for e in metadata}
    for (kind,src),local in sorted(table.items()):
        if kind!='item' or str(src) not in source:continue
        if 13308 <= src <= 13316: continue  # Client count visuals, never gameplay constants.
        # Source suffix prevents name collisions between states and generic native items (including D9 chest).
        display=metadata_by_id.get(local,{}).get('name') or source[str(src)].get('name')
        if not display:raise ValueError(f'Missing proven item name for source{src}')
        name=re.sub(r'[^A-Z0-9]+','_',display.upper()).strip('_')
        if '98' in source[str(src)]['fields']: name+='_NOTED'
        constants.append(f'    const val DMM2_{name}_OSRS_{src} = {local}\n')
    pos=old.rfind('}');new=old[:pos]+'\n    // DMM2 explicit cache mapping; review source/cache provenance before use.\n'+''.join(constants)+old[pos:]
    patch(out,'02-Items.kt'+suffix,path,old,new)
    # Nigel's exact OSRS GE tile. Wizard/Seer GE is the owner's relocation, not an OSRS spawn claim.
    path='data/world_edits.json';old=(ROOT/path).read_text(encoding='utf-8');data=json.loads(old)
    group=max(e.get('group',0) for e in data['edits'])+1
    additions=[]
    for src,x,z,note in [(8391,3152,3512,'DMM2 Nigel: wiki GE map3152/3512; rotation chosen for our plaza'),
                         (6516,3150,3512,'DMM2 Financial Wizard: owner GE relocation; proposed tile, collision check required'),
                         (6485,3150,3514,'DMM2 Financial Seer: owner GE relocation; proposed tile, collision check required')]:
        additions.append(dict(op='npc_spawn',id=table['npc',src],x=x,z=z,level=0,type=10,rot=4,note=note,group=group));group+=1
    # On the release only: remove the actual last location in this worktree's world edit history.
    trader=[e for e in data['edits'] if e.get('op')=='npc_spawn' and e.get('id')==14478]
    if trader:
        e=trader[-1]; additions.append(dict(op='npc_remove',id=14478,x=e['x'],z=e['z'],level=e['level'],type=10,rot=0,
            note='DMM2 release only: retired Breach Trader after migration/reward destinations reviewed',group=group))
    pos=old.rfind('\n  ]');assert pos>0
    new=old[:pos].rstrip()+',\n'+',\n'.join('    '+json.dumps(e,ensure_ascii=False) for e in additions)+old[pos:]
    json.loads(new)
    patch(out,'03-world_edits.json'+suffix,path,old,new)
    (out/'metadata-review.json').write_text(json.dumps(dict(plan_only=plan,source_stat_overrides=warnings,plan_metadata_collisions=conflicts,
        note='PLAN patches are for review only; regenerate using actual ids.tsv AND native items.generated.yml after apply.',
        reserved_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['data/cfg/items.yml',PLUGIN+'api/cfg/Items.kt','data/world_edits.json']}),indent=2)+'\n')
    return len(metadata)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan',action='store_true');p.add_argument('--ids',type=Path);p.add_argument('--generated-yml',type=Path)
    p.add_argument('--out',type=Path,default=DOC/'voorstel')
    args=p.parse_args()
    if args.plan:
        logs={n:(DOC/'phase4-output'/f'{n}.txt').read_text(encoding='utf-8') for n in ('items','npcs','seqs','spots','locs','ui232','wisp')}
        # Independent snapshots are NOT a coherent combined apply. Only explicit seq-batch mappings are used in this
        # review-only table; do not merge their independently allocated spot-sequence dependencies as if reserved.
        logs['spots']=re.sub(r' seq \d+->\d+', '', logs['spots'])
        table=mappings(logs);native=DOC/'phase4-output/items.plan.raw.yml'
    else:
        if not args.ids or not args.generated_yml:p.error('Actual --ids and native --generated-yml required')
        table={}
        for line in args.ids.read_text(encoding='utf-8-sig').splitlines():
            if not line.strip() or line.lstrip().startswith('#'):continue
            kind,source,local=line.split('\t');key=kind,int(source)
            if key in table:raise ValueError('Duplicate mapping')
            table[key]=int(local)
        native=args.generated_yml
    print(f'Wrote review patches for {propose(args.out,table,native,args.plan)} metadata rows; no reserved files edited')


if __name__=='__main__':main()
