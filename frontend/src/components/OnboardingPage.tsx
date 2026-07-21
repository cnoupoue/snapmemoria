import { useState } from 'react';

const INDEPENDENCE_DISCLAIMER =
  'This application is an independent, open-source local tool and is not affiliated, associated, authorized, endorsed by, or in any way officially connected with Snap Inc. or Snapchat.';

type OnboardingPageProps = {
  onAddSource: () => void;
};

export function OnboardingPage({ onAddSource }: OnboardingPageProps) {
  const [isFolderHelpOpen, setIsFolderHelpOpen] = useState(false);

  return (
    <section className="content onboarding-page">
      <div className="onboarding-hero">
        <p className="eyebrow">First launch</p>
        <h2>Welcome to Memoria Vault</h2>
        <p className="onboarding-lede">
          A private viewer for exported memories on this computer.
        </p>

        <div className="privacy-note">
          <strong>Local only.</strong>
          <span>Nothing is uploaded.</span>
        </div>

        <div className="onboarding-actions">
          <button
            className="primary-button"
            onClick={onAddSource}
            type="button"
          >
            Choose exported archive folder
          </button>
          <button
            className="secondary-button"
            onClick={() => setIsFolderHelpOpen((current) => !current)}
            type="button"
          >
            Where is the folder?
          </button>
        </div>
      </div>

      <div className="onboarding-grid">
        <section className="onboarding-panel">
          <ol className="onboarding-steps">
            <li>
              <span>1</span>
              <strong>Choose your export</strong>
              <p>Select the parent folder from your downloaded archive.</p>
            </li>
            <li>
              <span>2</span>
              <strong>Scan locally</strong>
              <p>Memoria Vault indexes files in place.</p>
            </li>
            <li>
              <span>3</span>
              <strong>Browse your archive</strong>
              <p>Photos, videos, favorites, and flashbacks stay on device.</p>
            </li>
          </ol>
        </section>

        <section className="folder-example-panel">
          <p className="eyebrow">Pick this folder</p>
          <pre className="folder-tree">{`exported-archive/
├── memories/
├── memories 2/
├── memories 3/
└── ...`}</pre>
          <p>Choose the folder that contains all memories folders.</p>
        </section>
      </div>

      {isFolderHelpOpen && (
        <section className="onboarding-help">
          <h3>Where to look</h3>
          <p>
            Unzip your downloaded archive, then select the top folder that
            groups the memories folders together. External drives work when
            connected.
          </p>
        </section>
      )}

      <p className="onboarding-disclaimer">{INDEPENDENCE_DISCLAIMER}</p>
    </section>
  );
}
