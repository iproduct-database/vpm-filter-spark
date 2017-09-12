use classifier1

# addIndexes pages
# addIndexes pagePatents

create table domains as select domain, count(distinct(url)) as numPages, count(*) as numPatentsWithDuplicates, count(distinct(patentNumber)) as numDistinctPatents from pagePatents group by domain;
# addIndexes david.domains

# commoncrawl has many duplicated urls. remove url duplicates:
create table tmp_one_id_by_url as select min(id) id from cc_pages group by url;
alter table tmp_one_id_by_url add index(id);
delete from cc_pages where id not in (select id from tmp_one_id_by_url); 
delete from cc_page_patents where page_id not in (select id from tmp_one_id_by_url);
drop table tmp_one_id_by_url;
