# DICOM2MP4
Convert DICOM files to MP4
Copy the DICOM folder to your PC.
For example C:\TEMP\DICOM
Chek the files IM_0001.dcm is preent in this folder.
The program will try to convert all the .dcm file to mp4 and also create image folder for example IM_0001_frames
Extracts frames from the DICOM file
Extracts metadata (Hospital name, Machine name, etc.) from the DICOM file
Adds extra space (50px) at the bottom to accommodate metadata.
Saves them as JPGs
Uses Xuggler to stitch them into an MP4 video
25 FPS playback speed
