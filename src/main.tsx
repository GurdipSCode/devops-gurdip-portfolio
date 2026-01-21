import * as Sentry from "@sentry/react";
import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import './index.css'

Sentry.init({
  dsn: "https://6ee0e5a17e903cf310be34e68a078f1d@o4509985210892288.ingest.de.sentry.io/4509985239597136",
  // Setting this option to true will send default PII data to Sentry.
  // For example, automatic IP address collection on events
  sendDefaultPii: true
});

createRoot(document.getElementById("root")!).render(<App />);