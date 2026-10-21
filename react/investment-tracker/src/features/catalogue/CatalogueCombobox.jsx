import { useId, useState } from "react";
import useCatalogueSearch from "./useCatalogueSearch";
import RequestError from "../../shared/components/RequestError";

/** Editable listbox combobox: focus remains on the input, never on a clickable div. */
export default function CatalogueCombobox({ id, inputRef, label, value, onChange, onSelect, enabled = true, minimumLength = 1,
  disabled = false, required = false, placeholder, inputClassName = "form-control", listClassName = "tracker-autocomplete-dropdown",
  optionClassName = "tracker-autocomplete-item d-flex align-items-center gap-2", renderOption }) {
  const generatedId = useId();
  const inputId = id || generatedId;
  const listId = `${inputId}-suggestions`;
  const [open, setOpen] = useState(false);
  const [activeId, setActiveId] = useState(null);
  const { results, error, loading, retry } = useCatalogueSearch(value, enabled, minimumLength);
  const expanded = open && enabled && !disabled && results.length > 0;
  const activeIndex = results.findIndex(item => item.id === activeId);
  const select = (item) => { setOpen(false); setActiveId(null); onSelect(item); };
  const onKeyDown = (event) => {
    if (event.key === "Escape") { event.preventDefault(); setOpen(false); setActiveId(null); return; }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      if (!enabled || !results.length) return;
      event.preventDefault(); setOpen(true);
      const direction = event.key === "ArrowDown" ? 1 : -1;
      const next = activeIndex < 0 ? (direction === 1 ? 0 : results.length - 1) : (activeIndex + direction + results.length) % results.length;
      setActiveId(results[next].id);
    } else if (event.key === "Enter" && expanded && activeIndex >= 0 && !event.nativeEvent.isComposing) {
      event.preventDefault(); select(results[activeIndex]);
    }
  };
  return <div className="position-relative" onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false); }}>
    <input ref={inputRef} id={inputId} aria-label={label} role="combobox" aria-autocomplete="list" aria-expanded={expanded}
      aria-controls={expanded ? listId : undefined} aria-activedescendant={expanded && activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined}
      aria-busy={loading} aria-describedby={error ? `${inputId}-error` : undefined}
      className={inputClassName} value={value} onChange={event => { setOpen(true); setActiveId(null); onChange(event.target.value); }}
      onFocus={() => setOpen(true)} onKeyDown={onKeyDown} disabled={disabled} required={required} placeholder={placeholder} autoComplete="off" />
    {expanded && <ul id={listId} role="listbox" aria-label={`${label || "Item"} suggestions`} className={`${listClassName} list-unstyled mb-0`}>
      {results.map((item, index) => <li id={`${listId}-${index}`} key={item.id} role="option" aria-selected={item.id === activeId}
        className={`${optionClassName}${item.id === activeId ? " catalogue-option-active" : ""}`}
        onMouseDown={event => event.preventDefault()} onClick={() => select(item)}>
        {renderOption ? renderOption(item) : <>
          {item.image && <img src={item.image} alt="" style={{ width: 40, height: 30, objectFit: "contain" }} />}
          <span className="text-white small">{item.name}</span>
        </>}
      </li>)}
    </ul>}
    {error && <div id={`${inputId}-error`} className="mt-2"><RequestError message={error} onRetry={retry} retryLabel="Retry search" /></div>}
    <span className="visually-hidden" role="status">{loading ? "Searching items..." : enabled && value.trim().length >= minimumLength ? `${results.length} suggestions available.` : ""}</span>
  </div>;
}
