    create extension postgis;
    
    create sequence app_users_SEQ start with 1 increment by 50;

    create sequence Borrow_SEQ start with 1 increment by 50;

    create sequence Card_SEQ start with 1 increment by 50;

    create sequence CardReference_SEQ start with 1 increment by 50;

    create sequence Deck_SEQ start with 1 increment by 50;

    create table app_users (
        id bigint not null,
        discordId varchar(255) unique,
        issuer varchar(255),
        password varchar(255),
        role varchar(255),
        username varchar(255) unique,
        location geometry,
        primary key (id)
    );

    create table Borrow (
        returned boolean not null,
        borrower_id bigint unique,
        createdAt timestamp(6) with time zone not null,
        id bigint not null,
        owner_id bigint unique,
        updatedAt timestamp(6) with time zone not null,
        primary key (id)
    );

    create table Borrow_Card (
        Borrow_id bigint not null,
        cards_id bigint not null unique
    );

    create table Card (
        isBorrowed boolean not null,
        language smallint check ((language between 0 and 3)),
        quality smallint check ((quality between 0 and 4)),
        borrower_id bigint,
        cardReference_id bigint,
        id bigint not null,
        owner_id bigint,
        primary key (id)
    );

    create table CardReference (
        isNew boolean not null,
        id bigint not null,
        imageUrl varchar(255),
        name varchar(255),
        riftboundId varchar(255),
        set varchar(255),
        primary key (id)
    );

    create table Deck (
        createdAt timestamp(6) with time zone not null,
        id bigint not null,
        owner_id bigint,
        updatedAt timestamp(6) with time zone not null,
        name varchar(255),
        primary key (id)
    );

    create table Deck_Card (
        Deck_id bigint not null,
        cards_id bigint not null unique
    );

    alter table if exists Borrow 
       add constraint FKrrqdw87fa20gux4iha86ptal5 
       foreign key (borrower_id) 
       references app_users;

    alter table if exists Borrow 
       add constraint FKs3ojmp1bkc5lq1clta2pcyk65 
       foreign key (owner_id) 
       references app_users;

    alter table if exists Borrow_Card 
       add constraint FKr3myprgiyu4gj52uxs4yf86b0 
       foreign key (cards_id) 
       references Card;

    alter table if exists Borrow_Card 
       add constraint FK53g0rxxcrvx4swxtvkqy1qnxl 
       foreign key (Borrow_id) 
       references Borrow;

    alter table if exists Card 
       add constraint FKm6fm4ilycf4fqc5vq3q5cv3hm 
       foreign key (borrower_id) 
       references app_users;

    alter table if exists Card 
       add constraint FKf8ha7p5425j2ar3q9xqsj4k8p 
       foreign key (cardReference_id) 
       references CardReference;

    alter table if exists Card 
       add constraint FK9w33tt74ee3cfqjg7exsbgtv1 
       foreign key (owner_id) 
       references app_users;

    alter table if exists Deck 
       add constraint FK3ooc6d00prtgpqe5airnt67io 
       foreign key (owner_id) 
       references app_users;

    alter table if exists Deck_Card 
       add constraint FKn66bvfc2b4rrxtgwmpxj61x92 
       foreign key (cards_id) 
       references Card;

    alter table if exists Deck_Card 
       add constraint FK8avhshpa560g08pq26hqgi8nf 
       foreign key (Deck_id) 
       references Deck;
INSERT INTO app_users (id, role, username, location) VALUES (1, 'user', 'arnono', 'SRID=4326;POINT(-118.4079 33.9434)'::geometry);
INSERT INTO cardreference (id, riftboundid, name, set, imageurl, isnew) VALUES (1, 'unl-229*-219', 'Vi - Piltover Enforcer (Signature)', 'UNL', 'https://cmsassets.rgpub.io/sanity/images/dsfx7636/game_data_live/0febae9c611339d9ed65c7ebe43237b5ec42c9a3-744x1039.png?accountingTag=RB', false);
INSERT INTO card (id, quality, language, isborrowed, owner_id, cardreference_id) VALUES (1, 0, 0, false, 1, 1);
INSERT INTO card (id, quality, language, isborrowed, owner_id, cardreference_id) VALUES (2, 0, 0, false, 1, 1);
alter sequence app_users_seq restart with 2;
alter sequence cardreference_seq restart with 2;
alter sequence card_seq restart with 3;
