// Material Symbols helper. `fill` toggles the filled variant used across the UI.
export default function Icon({ name, className = "", fill = false }) {
  return (
    <span className={`material-symbols-outlined${fill ? " icon-fill" : ""}${className ? " " + className : ""}`}>
      {name}
    </span>
  );
}
