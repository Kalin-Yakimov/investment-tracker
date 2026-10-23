export default function PriceAttribution({ includePriceSource = true }) {
  return <div className="text-end small text-secondary mt-3">
    {includePriceSource && <>Prices: <a className="text-secondary" href="https://csgotrader.app/prices/" target="_blank" rel="noopener noreferrer">CSGOTrader</a>{" · "}</>}
    <a className="text-secondary" href="https://www.exchangerate-api.com" target="_blank" rel="noopener noreferrer">Rates by ExchangeRate-API</a>
  </div>;
}
