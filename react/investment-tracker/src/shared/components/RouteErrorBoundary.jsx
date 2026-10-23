import { Component } from "react";

export default class RouteErrorBoundary extends Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch() { console.error("A page could not be displayed."); }
  render() {
    if (!this.state.failed) return this.props.children;
    return <div className="container py-5" role="alert">
      <h2>This page could not be displayed.</h2>
      <p>Reload the page to try again. If a save was in progress, check the latest data before submitting again.</p>
      <button type="button" className="btn btn-primary me-2" onClick={() => window.location.reload()}>Reload page</button>
      <a className="btn btn-secondary" href="/">Back to Portfolio</a>
    </div>;
  }
}
