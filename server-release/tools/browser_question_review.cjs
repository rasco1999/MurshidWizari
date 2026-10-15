const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES + '/playwright');

async function main() {
  const directory = path.resolve(process.argv[2]);
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'],
    executablePath: process.env.MURSHID_REVIEW_BROWSER || undefined });
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await page.goto(pathToFileURL(path.join(directory, 'Question_Review.html')).href);
  await page.evaluate(() => document.fonts.ready);
  const count = await page.evaluate(() => reviewAudit.records.length);
  const issues = [], ids = new Set();
  const widths = [320, 390, 768], themes = ['light', 'dark'];
  const colors = {};
  for (let offset = 0; offset < count; offset += 128) {
    const batchIds = await page.evaluate(offset => {
      const batch = reviewAudit.records.slice(offset, offset + 128);
      reviewAudit.renderList(batch);
      // Inspect the answer/explanation as well as the pre-answer card.
      document.querySelectorAll('.card > details:first-of-type').forEach(el => el.open = true);
      return batch.map(q => q.id);
    }, offset);
    batchIds.forEach(id => ids.add(id));
    for (const width of widths) {
      await page.setViewportSize({ width, height: 844 });
      for (const theme of themes) {
        const state = await page.evaluate(({ width, theme }) => {
          document.documentElement.dataset.theme = theme;
          const failures = [];
          for (const card of document.querySelectorAll('.card')) {
            for (const node of card.querySelectorAll('.question,.option,.answer,.explanation,.badge')) {
              const r = node.getBoundingClientRect(), bounds = card.getBoundingClientRect();
              if (node.scrollWidth > node.clientWidth + 2 || node.scrollHeight > node.clientHeight + 2 ||
                  r.right > bounds.right + 1 || r.left < bounds.left - 1)
                failures.push({ id: Number(card.dataset.id), width, theme, element: node.className,
                  scrollWidth: node.scrollWidth, clientWidth: node.clientWidth });
            }
          }
          const badge = document.querySelector('.badge'), style = getComputedStyle(badge);
          return { failures, foreground: style.color, background: style.backgroundColor };
        }, { width, theme });
        issues.push(...state.failures);
        colors[theme] = { foreground: state.foreground, background: state.background };
      }
    }
    if (offset % (128 * 24) === 0) console.log(JSON.stringify({ checked: Math.min(offset + 128, count), total: count }));
  }
  const samples = await page.evaluate(() => {
    const out = [];
    for (const type of [...new Set(reviewAudit.records.map(q => q.type))]) {
      const choices = reviewAudit.records.filter(q => q.type === type && q.published);
      choices.sort((a, b) => b.text.length - a.text.length);
      if (choices[0]) out.push(choices[0].id);
    }
    for (const grade of [...new Set(reviewAudit.records.map(q => q.grade))]) {
      const candidate = reviewAudit.records.find(q => q.grade === grade && q.published && q.text.length < 260);
      if (candidate && !out.includes(candidate.id)) out.push(candidate.id);
    }
    return out;
  });
  const imageDir = path.join(directory, 'visual_samples'); fs.mkdirSync(imageDir, { recursive: true });
  for (let index = 0; index < samples.length; index += 3) {
    await page.evaluate(ids => {
      document.querySelector('header').style.display = 'none';
      document.querySelector('.controls').style.display = 'none';
      document.querySelector('.pager').style.display = 'none';
      reviewAudit.renderList(ids.map(id => reviewAudit.records.find(q => q.id === id)));
    }, samples.slice(index, index + 3));
    await page.setViewportSize({ width: 390, height: 844 });
    for (const theme of themes) {
      await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
      await page.screenshot({ path: path.join(imageDir, 'questions-' + (index / 3 + 1) + '-' + theme + '.png'), fullPage: true });
    }
  }
  const result = { question_records_checked: ids.size, configurations_per_record: widths.length * themes.length,
    total_card_configuration_checks: ids.size * widths.length * themes.length,
    widths, themes, colors, clipping_or_overflow_findings: issues, representative_question_ids: samples,
    scope: 'Read-only HTML review-card layout, not an individual native-device inspection of every question',
    native_visual_review_complete: false, human_language_review_complete: false };
  fs.writeFileSync(path.join(directory, 'browser_layout_results.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify({ complete: true, records: ids.size, findings: issues.length, colors, samples }));
  await browser.close();
  if (issues.length) process.exitCode = 1;
}
main().catch(error => { console.error(error); process.exitCode = 1; });
