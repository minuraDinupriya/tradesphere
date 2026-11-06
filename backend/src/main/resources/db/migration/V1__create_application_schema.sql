-- Establish a namespace without introducing business tables before their requirements.
CREATE SCHEMA tradesphere;
COMMENT ON SCHEMA tradesphere IS 'TradeSphere business tables will be introduced by later migrations';
