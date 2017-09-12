# Create database
create database classifier1 character set utf8mb4 collate utf8mb4_unicode_ci;

use classifier1

create table VPM30Domains (domain varchar(255), index(domain));
insert into VPM30Domains values ("ipc.com"), ("hill-rom.com"), ("kimberly-clark.com"), ("tivo.com"), ("pg.com"), ("smarttech.com"), ("welchallyn.com"), ("invue.com"), ("novonordisk.com"), ("callawaygolf.com"), ("sageglass.com"), ("swisslog.com"), ("muellerwaterproducts.com"), ("rmspumptools.com"), ("activepower.com"), ("sjm.com"), ("viatech.com"), ("dolby.com"), ("symantec.com"), ("mallinckrodt.com"), ("allstate.com"), ("rapiscansystems.com"), ("davisnet.com"), ("abaxis.com"), ("BlackBerry.com"), ("bostonscientific.com"), ("logitech.com"), ("sunpower.com"), ("duracell.com");

# addIndexes VPM30Domains

create table patstat_us_one_author_per_patent AS
select
    A.PUBLN_NR,
    A.PUBLN_KIND,
    A.PUBLN_DATE,
    C.PERSON_CTRY_CODE,
    C.PERSON_NAME,
    C.DOC_STD_NAME,
    C.HAN_NAME
from
    patstat_2015a.TLS211_PAT_PUBLN A,
    patstat_2015a.TLS227_PERS_PUBLN B,
    patstat_2015a.TLS906_PERSON C
where
    A.pat_publn_id = B.pat_publn_id
        AND B.applt_seq_nr > 0
        AND B.person_id = C.person_id
        AND A.publn_auth = 'US'
        AND C.sector != 'INDIVIDUAL'
group by A.PUBLN_NR;                            # this groupby enforces to take only one person per patent

# addIndexes patstat_us_one_author_per_patent
