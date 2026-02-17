import requests

def extract_adult_iptv():
    # 目标源地址
    url = "http://adultiptv.net/chs.m3u"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
    
    try:
        response = requests.get(url, headers=headers, timeout=15)
        response.encoding = 'utf-8'
        if response.status_code == 200:
            lines = response.text.split('\n')
            final_output = ["精品频道,#genre#"]
            
            current_name = ""
            for line in lines:
                line = line.strip()
                if "#EXTINF" in line:
                    # 提取逗号后面的频道名称
                    name_parts = line.split(',')
                    if len(name_parts) > 1:
                        current_name = name_parts[-1].strip()
                elif line.startswith("http"):
                    # 匹配到链接，与名称组合
                    if current_name:
                        final_output.append(f"{current_name},{line}")
                        current_name = "" # 重置名称寻找下一个
            
            return "\n".join(final_output)
    except Exception as e:
        return f"提取失败: {e}"
    return "未能获取到内容"

if __name__ == "__main__":
    content = extract_adult_iptv()
    # 写入你的 migu.txt 文件
    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write(content)
    print("提取并同步成功")
