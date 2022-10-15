package com.idormy.sms.forwarder.fragment

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.idormy.sms.forwarder.App
import com.idormy.sms.forwarder.R
import com.idormy.sms.forwarder.adapter.WidgetItemAdapter
import com.idormy.sms.forwarder.core.BaseFragment
import com.idormy.sms.forwarder.databinding.FragmentClientBinding
import com.idormy.sms.forwarder.server.model.BaseResponse
import com.idormy.sms.forwarder.server.model.ConfigData
import com.idormy.sms.forwarder.utils.*
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xhttp2.XHttp
import com.xuexiang.xhttp2.cache.model.CacheMode
import com.xuexiang.xhttp2.callback.SimpleCallBack
import com.xuexiang.xhttp2.exception.ApiException
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xpage.base.XPageFragment
import com.xuexiang.xpage.core.PageOption
import com.xuexiang.xpage.model.PageInfo
import com.xuexiang.xrouter.utils.TextUtils
import com.xuexiang.xui.adapter.recyclerview.RecyclerViewHolder
import com.xuexiang.xui.utils.CountDownButtonHelper
import com.xuexiang.xui.utils.DensityUtils
import com.xuexiang.xui.utils.ResUtils
import com.xuexiang.xui.utils.WidgetUtils
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import com.xuexiang.xutil.XUtil

@Suppress("PrivatePropertyName", "PropertyName")
@Page(name = "主动控制·客户端")
class ClientFragment : BaseFragment<FragmentClientBinding?>(), View.OnClickListener, RecyclerViewHolder.OnItemClickListener<PageInfo> {

    val TAG: String = ClientFragment::class.java.simpleName
    private var appContext: App? = null
    private var serverConfig: ConfigData? = null
    private var serverHistory: MutableMap<String, String> = mutableMapOf()
    private var mCountDownHelper: CountDownButtonHelper? = null

    override fun initViews() {
        appContext = requireActivity().application as App

        //测试按钮增加倒计时，避免重复点击
        mCountDownHelper = CountDownButtonHelper(binding!!.btnServerTest, 3)
        mCountDownHelper!!.setOnCountDownListener(object : CountDownButtonHelper.OnCountDownListener {
            override fun onCountDown(time: Int) {
                binding!!.btnServerTest.text = String.format(getString(R.string.seconds_n), time)
            }

            override fun onFinished() {
                binding!!.btnServerTest.text = getString(R.string.server_test)
            }
        })

        WidgetUtils.initGridRecyclerView(binding!!.recyclerView, 3, DensityUtils.dp2px(1f))
        val widgetItemAdapter = WidgetItemAdapter(CLIENT_FRAGMENT_LIST)
        widgetItemAdapter.setOnItemClickListener(this)
        binding!!.recyclerView.adapter = widgetItemAdapter

        //取出历史记录
        val history = HttpServerUtils.serverHistory
        if (!TextUtils.isEmpty(history)) {
            serverHistory = Gson().fromJson(history, object : TypeToken<MutableMap<String, String>>() {}.type)
        }

        if (CommonUtils.checkUrl(HttpServerUtils.serverAddress)) queryConfig(false)
    }

    override fun initTitle(): TitleBar? {
        val titleBar = super.initTitle()!!.setImmersive(false)
        //纯客户端模式
        if (SettingUtils.enablePureClientMode) {
            titleBar.setTitle(R.string.app_name).setSubTitle(getString(R.string.menu_client)).disableLeftView()
            titleBar.addAction(object : TitleBar.ImageAction(R.drawable.ic_logout) {
                @SingleClick
                override fun performAction(view: View) {
                    XToastUtils.success(getString(R.string.exit_pure_client_mode))
                    SettingUtils.enablePureClientMode = false
                    XUtil.exitApp()
                }
            })
        } else {
            titleBar.setTitle(R.string.menu_client)
        }
        return titleBar
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentClientBinding {
        return FragmentClientBinding.inflate(inflater, container, false)
    }

    override fun initListeners() {
        binding!!.etServerAddress.setText(HttpServerUtils.serverAddress)
        binding!!.etServerAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                HttpServerUtils.serverAddress = binding!!.etServerAddress.text.toString().trim().trimEnd('/')
            }
        })

        //安全措施
        var safetyMeasuresId = R.id.rb_safety_measures_none
        when (HttpServerUtils.clientSafetyMeasures) {
            1 -> {
                safetyMeasuresId = R.id.rb_safety_measures_sign
                binding!!.tvSignKey.text = getString(R.string.sign_key)
            }
            2 -> {
                safetyMeasuresId = R.id.rb_safety_measures_rsa
                binding!!.tvSignKey.text = getString(R.string.public_key)
            }
            else -> {
                binding!!.layoutSignKey.visibility = View.GONE
            }
        }
        binding!!.rgSafetyMeasures.check(safetyMeasuresId)
        binding!!.rgSafetyMeasures.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            var safetyMeasures = 0
            binding!!.layoutSignKey.visibility = View.GONE
            when (checkedId) {
                R.id.rb_safety_measures_sign -> {
                    safetyMeasures = 1
                    binding!!.tvSignKey.text = getString(R.string.sign_key)
                    binding!!.layoutSignKey.visibility = View.VISIBLE
                }
                R.id.rb_safety_measures_rsa -> {
                    safetyMeasures = 2
                    binding!!.tvSignKey.text = getString(R.string.public_key)
                    binding!!.layoutSignKey.visibility = View.VISIBLE
                }
                else -> {}
            }
            HttpServerUtils.clientSafetyMeasures = safetyMeasures
        }

        binding!!.etSignKey.setText(HttpServerUtils.clientSignKey)
        binding!!.etSignKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                HttpServerUtils.clientSignKey = binding!!.etSignKey.text.toString().trim()
            }
        })

        binding!!.btnServerHistory.setOnClickListener(this)
        binding!!.btnServerTest.setOnClickListener(this)
    }

    @SingleClick
    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_server_history -> {
                if (serverHistory.isEmpty()) {
                    XToastUtils.warning(getString(R.string.no_server_history))
                    return
                }
                Log.d(TAG, "serverHistory = $serverHistory")

                MaterialDialog.Builder(requireContext()).title(R.string.server_history).items(serverHistory.keys).itemsCallbackSingleChoice(0) { _: MaterialDialog?, _: View?, _: Int, text: CharSequence ->
                    //XToastUtils.info("$which: $text")
                    val matches = Regex("【(.*)】(.*)", RegexOption.IGNORE_CASE).findAll(text).toList().flatMap(MatchResult::groupValues)
                    Log.i(TAG, "matches = $matches")
                    if (matches.isNotEmpty()) {
                        binding!!.etServerAddress.setText(matches[2])
                    } else {
                        binding!!.etServerAddress.setText(text)
                    }
                    val signKey = serverHistory[text].toString()
                    if (!TextUtils.isEmpty(signKey)) {
                        val keyMatches = Regex("(.*)##(.*)", RegexOption.IGNORE_CASE).findAll(signKey).toList().flatMap(MatchResult::groupValues)
                        Log.i(TAG, "keyMatches = $keyMatches")
                        if (keyMatches.isNotEmpty()) {
                            binding!!.etSignKey.setText(keyMatches[1])
                            var safetyMeasuresId = R.id.rb_safety_measures_none
                            when (keyMatches[2]) {
                                "1" -> {
                                    safetyMeasuresId = R.id.rb_safety_measures_sign
                                    binding!!.tvSignKey.text = getString(R.string.sign_key)
                                }
                                "2" -> {
                                    safetyMeasuresId = R.id.rb_safety_measures_rsa
                                    binding!!.tvSignKey.text = getString(R.string.public_key)
                                }
                                else -> {
                                    binding!!.tvSignKey.visibility = View.GONE
                                    binding!!.etSignKey.visibility = View.GONE
                                }
                            }
                            binding!!.rgSafetyMeasures.check(safetyMeasuresId)
                        } else {
                            binding!!.etSignKey.setText(serverHistory[text])
                        }
                    }
                    true // allow selection
                }.positiveText(R.string.select).negativeText(R.string.cancel).neutralText(R.string.clear_history).neutralColor(ResUtils.getColors(R.color.red)).onNeutral { _: MaterialDialog?, _: DialogAction? ->
                    serverHistory.clear()
                    HttpServerUtils.serverHistory = ""
                }.show()
            }
            R.id.btn_server_test -> {
                if (!CommonUtils.checkUrl(HttpServerUtils.serverAddress)) {
                    XToastUtils.error(getString(R.string.invalid_service_address))
                    return
                }
                queryConfig(true)
            }
            else -> {}
        }
    }

    override fun onItemClick(itemView: View, item: PageInfo, position: Int) {
        try {
            if (item.name != ResUtils.getString(R.string.api_clone) && !CommonUtils.checkUrl(HttpServerUtils.serverAddress)) {
                XToastUtils.error(getString(R.string.invalid_service_address))
                serverConfig = null
                return
            }
            if (serverConfig == null && item.name != ResUtils.getString(R.string.api_clone)) {
                XToastUtils.error(getString(R.string.click_test_button_first))
                return
            }
            if (serverConfig != null && ((item.name == ResUtils.getString(R.string.api_sms_send) && !serverConfig!!.enableApiSmsSend) || (item.name == ResUtils.getString(R.string.api_sms_query) && !serverConfig!!.enableApiSmsQuery) || (item.name == ResUtils.getString(R.string.api_call_query) && !serverConfig!!.enableApiCallQuery) || (item.name == ResUtils.getString(R.string.api_contact_query) && !serverConfig!!.enableApiContactQuery) || (item.name == ResUtils.getString(R.string.api_battery_query) && !serverConfig!!.enableApiBatteryQuery) || (item.name == ResUtils.getString(R.string.api_wol) && !serverConfig!!.enableApiWol))) {
                XToastUtils.error(getString(R.string.disabled_on_the_server))
                return
            }
            @Suppress("UNCHECKED_CAST") PageOption.to(Class.forName(item.classPath) as Class<XPageFragment>) //跳转的fragment
                .setNewActivity(true).open(this)
        } catch (e: Exception) {
            e.printStackTrace()
            XToastUtils.error(e.message.toString())
        }
    }

    private fun queryConfig(needToast: Boolean) {
        val requestUrl: String = HttpServerUtils.serverAddress + "/config/query"
        Log.i(TAG, "requestUrl:$requestUrl")

        val msgMap: MutableMap<String, Any> = mutableMapOf()
        val timestamp = System.currentTimeMillis()
        msgMap["timestamp"] = timestamp

        val clientSignKey = HttpServerUtils.clientSignKey.toString()
        if ((HttpServerUtils.clientSafetyMeasures == 1 || HttpServerUtils.clientSafetyMeasures == 2) && TextUtils.isEmpty(clientSignKey)) {
            if (needToast) XToastUtils.error("请输入签名密钥或RSA公钥")
            return
        }

        if (HttpServerUtils.clientSafetyMeasures == 1 && !TextUtils.isEmpty(clientSignKey)) {
            msgMap["sign"] = HttpServerUtils.calcSign(timestamp.toString(), clientSignKey)
        }
        val dataMap: MutableMap<String, Any> = mutableMapOf()
        msgMap["data"] = dataMap

        var requestMsg: String = Gson().toJson(msgMap)
        Log.i(TAG, "requestMsg:$requestMsg")

        val postRequest = XHttp.post(requestUrl)
            .keepJson(true)
            .timeOut((SettingUtils.requestTimeout * 1000).toLong()) //超时时间10s
            .cacheMode(CacheMode.NO_CACHE).timeStamp(true)

        if (HttpServerUtils.clientSafetyMeasures == 2) {
            val publicKey = RSACrypt.getPublicKey(HttpServerUtils.clientSignKey.toString())
            try {
                requestMsg = Base64.encode(requestMsg.toByteArray())
                requestMsg = RSACrypt.encryptByPublicKey(requestMsg, publicKey)
                Log.i(TAG, "requestMsg: $requestMsg")
            } catch (e: Exception) {
                if (needToast) XToastUtils.error(ResUtils.getString(R.string.request_failed) + e.message)
                e.printStackTrace()
                return
            }
            postRequest.upString(requestMsg)
        } else {
            postRequest.upJson(requestMsg)
        }

        if (needToast) mCountDownHelper?.start()
        postRequest.execute(object : SimpleCallBack<String>() {
            override fun onError(e: ApiException) {
                XToastUtils.error(e.displayMessage)
                if (needToast) mCountDownHelper?.finish()
            }

            override fun onSuccess(response: String) {
                Log.i(TAG, response)
                try {
                    var json = response
                    if (HttpServerUtils.clientSafetyMeasures == 2) {
                        val publicKey = RSACrypt.getPublicKey(HttpServerUtils.clientSignKey.toString())
                        json = RSACrypt.decryptByPublicKey(json, publicKey)
                        json = String(Base64.decode(json))
                    }
                    val resp: BaseResponse<ConfigData> = Gson().fromJson(json, object : TypeToken<BaseResponse<ConfigData>>() {}.type)
                    if (resp.code == 200) {
                        serverConfig = resp.data!!
                        if (needToast) XToastUtils.success(ResUtils.getString(R.string.request_succeeded))
                        //删除3.0.8之前保存的记录
                        serverHistory.remove(HttpServerUtils.serverAddress.toString())
                        //添加到历史记录
                        val key = "【${serverConfig?.extraDeviceMark}】${HttpServerUtils.serverAddress.toString()}"
                        if (TextUtils.isEmpty(HttpServerUtils.clientSignKey)) {
                            serverHistory[key] = "SMSFORWARDER##" + HttpServerUtils.clientSafetyMeasures.toString()
                        } else {
                            serverHistory[key] = HttpServerUtils.clientSignKey + "##" + HttpServerUtils.clientSafetyMeasures.toString()
                        }
                        HttpServerUtils.serverHistory = Gson().toJson(serverHistory)
                        HttpServerUtils.serverConfig = Gson().toJson(serverConfig)
                    } else {
                        if (needToast) XToastUtils.error(ResUtils.getString(R.string.request_failed) + resp.msg)
                    }
                    if (needToast) mCountDownHelper?.finish()
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (needToast) {
                        XToastUtils.error(ResUtils.getString(R.string.request_failed) + response)
                        mCountDownHelper?.finish()
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        if (mCountDownHelper != null) mCountDownHelper!!.recycle()
        super.onDestroyView()
    }

}