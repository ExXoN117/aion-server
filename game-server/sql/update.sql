/*
 * DB changes since 58a2b5f (07.09.2020)
 */

-- remove old event items
DELETE FROM inventory WHERE item_id IN (182007170, 185000185, 185000186, 186000111, 186000127, 186000175, 186000177, 186000381, 186000387, 186000388, 186000389, 186000406, 186000407, 188051090, 188051091, 188052318, 188052625, 188052640, 188052642, 188053101, 188053185, 188053915, 188054028, 188054029, 188100091, 188100092, 188100093, 188100094, 188100124, 188100125, 188100126, 188100127, 188100252, 188100253, 188100254, 188100255, 188100256);