/**
 * 本机头像：把微信 chooseAvatar 返回的临时文件转存到本地，路径存 storage。
 * 只存本机，换设备或清缓存后自动回退到昵称首字。
 */
const storage = require('./storage');

const KEY = 'avatarPath';

/** 读取头像路径；文件被系统清理时自动回退为空 */
function read() {
  const path = storage.get(KEY, '');
  if (!path) {
    return '';
  }
  try {
    wx.getFileSystemManager().accessSync(path);
    return path;
  } catch (e) {
    storage.remove(KEY);
    return '';
  }
}

/** 保存临时头像，resolve 持久路径 */
function save(tempFilePath) {
  return new Promise((resolve, reject) => {
    const fs = wx.getFileSystemManager();
    fs.saveFile({
      tempFilePath,
      success: (res) => {
        const old = storage.get(KEY, '');
        if (old && old !== res.savedFilePath) {
          fs.unlink({ filePath: old, fail: () => {} });
        }
        storage.set(KEY, res.savedFilePath);
        resolve(res.savedFilePath);
      },
      fail: reject
    });
  });
}

function remove() {
  const old = storage.get(KEY, '');
  if (old) {
    wx.getFileSystemManager().unlink({ filePath: old, fail: () => {} });
  }
  storage.remove(KEY);
}

module.exports = {
  read,
  save,
  remove
};
