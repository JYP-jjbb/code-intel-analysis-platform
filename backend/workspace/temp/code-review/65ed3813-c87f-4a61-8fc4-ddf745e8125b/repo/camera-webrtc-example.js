// WebRTC 相机示例代码
// 这是真正在PC端打开摄像头的唯一方法

async function openCamera() {
  try {
    // 请求访问摄像头
    const stream = await navigator.mediaDevices.getUserMedia({ 
      video: { facingMode: 'user' } // 前置摄像头
    });
    
    // 创建video元素显示预览
    const video = document.createElement('video');
    video.srcObject = stream;
    video.play();
    
    // 创建canvas捕获图片
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    
    // 拍照
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0);
    
    // 转换为Blob上传
    canvas.toBlob(async (blob) => {
      const formData = new FormData();
      formData.append('file', blob, 'photo.jpg');
      
      // 上传
      await uploadChatImage(formData.get('file'));
    }, 'image/jpeg');
    
    // 关闭摄像头
    stream.getTracks().forEach(track => track.stop());
    
  } catch (error) {
    console.error('无法访问摄像头:', error);
  }
}

