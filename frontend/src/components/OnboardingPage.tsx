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
          Rediscover your memories, privately. Choose your exported archive
          folder and browse it from this computer.
        </p>

        <div className="privacy-note">
          <strong>Your files stay on your computer.</strong>
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
            Learn where to find my export folder
          </button>
        </div>
      </div>

      <div className="onboarding-grid">
        <section className="onboarding-panel">
          <ol className="onboarding-steps">
            <li>
              <span>Step 1</span>
              <strong>Choose your exported archive folder.</strong>
              <p>
                Use the folder from your downloaded data export. Compatible
                export folder structures are supported descriptively.
              </p>
            </li>
            <li>
              <span>Step 2</span>
              <strong>Add the parent folder.</strong>
              <p>
                Select the folder that contains all Memories folders, not just
                one folder inside it.
              </p>
            </li>
            <li>
              <span>Step 3</span>
              <strong>Scan locally and start browsing.</strong>
              <p>
                Memoria Vault indexes memories in place so your archive becomes
                easy to explore.
              </p>
            </li>
          </ol>
        </section>

        <section className="folder-example-panel">
          <p className="eyebrow">Correct folder</p>
          <pre className="folder-tree">{`exported-archive/
├── memories/
├── memories 2/
├── memories 3/
└── ...`}</pre>
          <p>Select the parent exported archive folder.</p>
          <p>
            Do not select only <strong>memories/</strong> if your export
            contains <strong>memories 2</strong>, <strong>memories 3</strong>,
            or more folders.
          </p>
        </section>
      </div>

      {isFolderHelpOpen && (
        <section className="onboarding-help">
          <h3>Where to look</h3>
          <p>
            After downloading your archive data, unzip the export and look for
            the folder that groups your memories folders together. External
            drives are supported as long as the drive is connected when you scan
            or view media.
          </p>
        </section>
      )}

      <section className="onboarding-help">
        <h3>Independent tool</h3>
        <p>{INDEPENDENCE_DISCLAIMER}</p>
        <p>
          Compatible Snapchat export formats may be read locally. Compatibility
          references are descriptive only.
        </p>
      </section>
    </section>
  );
}
