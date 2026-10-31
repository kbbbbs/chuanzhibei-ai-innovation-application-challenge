import { ElMessageBox } from 'element-plus'

function isCancel(err) {
  return err === 'cancel' || err?.type === 'cancel'
}

export function useConfirm() {
  const confirmDelete = (title = '确认删除？', options = {}) => {
    return ElMessageBox.confirm(title, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      ...options,
    })
      .then(() => true)
      .catch((err) => {
        if (isCancel(err)) return false
        throw err
      })
  }

  const confirmSubmit = (title = '确认提交？', options = {}) => {
    return ElMessageBox.confirm(title, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
      ...options,
    })
      .then(() => true)
      .catch((err) => {
        if (isCancel(err)) return false
        throw err
      })
  }

  return { confirmDelete, confirmSubmit }
}
