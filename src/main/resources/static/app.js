const movieInput = document.querySelector('#movie');
const dropZone = document.querySelector('#drop-zone');
const fileLabel = document.querySelector('#file-label');
const convertButton = document.querySelector('#convert');
const buttonLabel = document.querySelector('#button-label');
const status = document.querySelector('#status');
let selectedFile = null;

function setFile(file) {
  if (!file) return;
  if (!file.name.toLowerCase().endsWith('.mov')) {
    status.textContent = 'Please choose a file ending in .mov.';
    status.className = 'status error';
    return;
  }
  selectedFile = file;
  fileLabel.textContent = `${file.name} · ${(file.size / 1024 / 1024).toFixed(1)} MB`;
  convertButton.disabled = false;
  status.textContent = 'Ready to convert. Your original MOV will not be changed.';
  status.className = 'status';
}

movieInput.addEventListener('change', () => setFile(movieInput.files[0]));
['dragenter', 'dragover'].forEach((eventName) => dropZone.addEventListener(eventName, (event) => {
  event.preventDefault();
  dropZone.classList.add('dragging');
}));
['dragleave', 'drop'].forEach((eventName) => dropZone.addEventListener(eventName, (event) => {
  event.preventDefault();
  dropZone.classList.remove('dragging');
}));
dropZone.addEventListener('drop', (event) => setFile(event.dataTransfer.files[0]));

document.querySelectorAll('input[name="quality"]').forEach((input) => {
  input.addEventListener('change', () => {
    document.querySelectorAll('.quality-option').forEach((label) => label.classList.remove('chosen'));
    input.closest('.quality-option').classList.add('chosen');
  });
});

convertButton.addEventListener('click', async () => {
  if (!selectedFile) return;
  const form = new FormData();
  form.append('movie', selectedFile);
  form.append('quality', document.querySelector('input[name="quality"]:checked').value);
  convertButton.disabled = true;
  buttonLabel.textContent = 'Converting locally…';
  status.textContent = 'Your browser is sending the file only to this Mac’s local server.';
  status.className = 'status working';

  try {
    const response = await fetch('/api/convert', { method: 'POST', body: form });
    if (!response.ok) {
      const message = await response.json().catch(() => ({ message: 'Conversion failed.' }));
      throw new Error(message.message);
    }
    const blob = await response.blob();
    const downloadURL = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = downloadURL;
    link.download = selectedFile.name.replace(/\.mov$/i, '') + '-google.mp4';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(downloadURL);
    status.textContent = 'Done — your MP4 download has started.';
    status.className = 'status success';
  } catch (error) {
    status.textContent = error.message || 'The MOV could not be converted.';
    status.className = 'status error';
  } finally {
    convertButton.disabled = false;
    buttonLabel.textContent = 'Convert to MP4';
  }
});
