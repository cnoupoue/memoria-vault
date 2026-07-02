import { readFileSync } from 'node:fs';

const publicFacingFiles = [
  'README.md',
  'THIRD_PARTY_NOTICES.md',
  'docs/CONTRIBUTING.md',
  'docs/SECURITY.md',
  'docs/RELEASE_NOTES_TEMPLATE.md',
  'docs/technical-contribution-guide.md',
  'frontend/index.html',
  'frontend/README.md',
  'frontend/src/App.tsx',
  'frontend/src/components/FlashbacksPage.tsx',
  'frontend/src/components/MemoryViewer.tsx',
  'frontend/src/components/OnboardingPage.tsx',
  'frontend/src/components/SettingsPage.tsx',
  'frontend/src/styles.css',
  'Makefile',
  'packaging/macos/ffmpeg/README.md',
];

const prohibitedPatterns = [
  /SnapMemoria/,
  /Your Snapchat archive/,
  /Choose Snapchat export folder/,
  /Add your Snapchat export/,
  /Snapchat Memories browser/,
  /Official Snapchat/i,
  /made by Snap/i,
  /made by Snapchat/i,
  /partnered with Snap/i,
  /partnered with Snapchat/i,
];

const failures = [];

for (const file of publicFacingFiles) {
  const contents = readFileSync(file, 'utf8');

  for (const pattern of prohibitedPatterns) {
    if (pattern.test(contents)) {
      failures.push(`${file}: ${pattern}`);
    }
  }
}

if (failures.length > 0) {
  console.error('Public-facing branding check failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}
