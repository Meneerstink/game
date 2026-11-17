"""Own focused tests; synthetic logs prove the handoff parser, not a cache apply."""
from pathlib import Path
import tempfile
import unittest
import dmm2_ids_from_apply as ids


def fixture():
    rows = {
        'items': '\n'.join(f"PLAN item {26000+i} 'item' <- upstream_item:{s} bytes=10 models=[]" for i,s in enumerate(ids.PARENTS)),
        'npcs': '\n'.join(f"PLAN npc {s} 'npc' -> {17000+i} bas=1 models=[]->[] heads=[]->[] seqs={{808=808}}" for i,s in enumerate((6516,6485,8391,8390,15569,15570))),
        'seqs': '\n'.join(f'PLAN seq {s} -> {18000+i}' for i,s in enumerate((11444,11810,11811,11812,11935,11936,11937,*range(13701,13715),14497))),
        'spots': '\n'.join(f'PLAN spotanim {s} -> {4000+i} model 1->2 seq 11444->18000' for i,s in enumerate((3030,3031,3032,3033,*range(3565,3572),4020))),
        'locs': '\n'.join(f"PLAN loc {s} 'Rug' -> {64000+i} models=[]->[]" for i,s in enumerate((54114,54115,54116))),
        'ui232': 'PLAN osrs 232 -> interface 1200: 3 components',
        'wisp': 'PLAN skeletal npc 15571 -> 17006 bas=1 models=[49222, 49218, 49221, 49224]->[65536,65537,65538,65539] heads=[] seqs={10230=18022, 10233=18023}',
        'wisp-sidecar': 'VERIFY_OK both targets hold intended bytes and names',
    }
    for i,(job,text) in enumerate(rows.items()):
        footer = f'TRANSACTION tx-fixture-{i} applied=true finalized=1 recovered=0' if job == 'items' else f'APPLIED transaction=tx-fixture-{i} writes=1'
        rows[job] = text + '\n' + footer + '\n'
    return rows


class Dmm2ApplyIdsTests(unittest.TestCase):
    def test_complete_apply_and_wisp_arguments(self):
        logs = fixture()
        for job,text in logs.items(): ids.verify_log(job,text)
        result = ids.mappings(logs)
        self.assertEqual(26000, result['item',13307])
        self.assertEqual(17006, result['npc',15571])
        self.assertEqual(23822, result['item',28564])
        self.assertNotIn(('item',34607),result)
        self.assertEqual(['17006','49222,49218,49221,49224','18022=10230','18023=10233'], ids.wisp_args(logs['wisp']))

    def test_all_real_committed_plans_are_rejected(self):
        root = Path(__file__).parent/'preflight-output'
        for job,file in [('items','bundle-item'),('npcs','bundle-npc-classic'),('seqs','bundle-seq'),('spots','bundle-spotanim'),('locs','bundle-loc'),('ui232','interface-232'),('wisp','npc-skeletal-15571'),('wisp-sidecar','wisp-sidecar')]:
            with self.subTest(job=job), self.assertRaises(ValueError):
                ids.verify_log(job, (root/(file+'.txt')).read_text(encoding='utf-8'))

    def test_missing_conflicting_duplicate_and_overflow_ids(self):
        for extra in ["PLAN item 999 'bad' <- upstream_item:13307 bytes=1", "PLAN item 26000 'duplicate' <- upstream_item:999999 bytes=1", "PLAN seq 999999999999 -> 1"]:
            logs=fixture(); logs['items'] += extra
            with self.subTest(extra=extra),self.assertRaises(ValueError): ids.mappings(logs)
        logs=fixture();del logs['locs']
        with self.assertRaises(ValueError): ids.mappings(logs)

    def test_errors_and_partial_footer_are_rejected(self):
        text=fixture()['npcs']
        for broken in [text+'VERIFY_ERROR: bad byte',text+'Exception in thread main',text.replace('APPLIED','PREFLIGHT')]:
            with self.assertRaises(ValueError): ids.verify_log('npcs',broken)
        with self.assertRaises(ValueError): ids.verify_log('wisp-sidecar','APPLIED transaction=tx-fixture writes=1')

    def test_publication_is_atomic_and_failures_preserve_existing_file(self):
        with tempfile.TemporaryDirectory() as directory:
            d=Path(directory); output=d/'ids.tsv'; logs=fixture()
            for name,text in logs.items(): (d/(name+'.txt')).write_text(text)
            self.assertGreater(ids.generate(d,output),90)
            before=output.read_bytes()
            (d/'spots.txt').write_text('PREFLIGHT transaction=tx-incomplete')
            with self.assertRaises(ValueError):ids.generate(d,output)
            self.assertEqual(before,output.read_bytes())
            self.assertFalse((d/'ids.tsv.tmp').exists())

    def test_wisp_source_models_or_sequence_set_cannot_drift(self):
        text=fixture()['wisp']
        for invalid in [text.replace('49222','99999'),text.replace('10233=18023','99999=18023')]:
            with self.assertRaises(ValueError): ids.wisp_args(invalid)


if __name__ == '__main__': unittest.main()
