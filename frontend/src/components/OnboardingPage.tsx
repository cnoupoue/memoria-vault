import { useState } from 'react';

type OnboardingPageProps = {
  onAddSource: () => void;
};

export function OnboardingPage({ onAddSource }: OnboardingPageProps) {
  const [isFolderHelpOpen, setIsFolderHelpOpen] = useState(false);

  return (
    <section className="content onboarding-page">
      <div className="onboarding-hero">
        <p className="eyebrow">First launch</p>
        <h2>Welcome to SnapMemoria</h2>
        <p className="onboarding-lede">
          Your Snapchat Memories should not stay buried in folders. SnapMemoria
          helps you browse exported Memories by year, month, and flashbacks
          directly from your computer or USB drive.
        </p>

        <div className="privacy-note">
          <strong>Your files stay private.</strong>
          <span>Nothing is uploaded.</span>
        </div>

        <div className="onboarding-actions">
          <button
            className="primary-button"
            onClick={onAddSource}
            type="button"
          >
            Add your Snapchat export
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
              <strong>Find your Snapchat export folder.</strong>
              <p>Use the folder from your downloaded Snapchat data export.</p>
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
                SnapMemoria indexes metadata on your computer so your archive
                becomes easy to explore.
              </p>
            </li>
          </ol>
        </section>

        <section className="folder-example-panel">
          <p className="eyebrow">Correct folder</p>
          <pre className="folder-tree">{`snapchat-memories/
├── memories/
├── memories 2/
├── memories 3/
└── ...`}</pre>
          <p>
            Select the parent <strong>snapchat-memories</strong> folder.
          </p>
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
            After downloading your Snapchat data, unzip the export and look for
            the folder that groups your Memories folders together. External USB
            drives are supported as long as the drive is connected when you scan
            or view media.
          </p>
        </section>
      )}
    </section>
  );
}
